(ns dj.classloader
  (:import [java.net URISyntaxException])
  (:require [dj]
            [dj.repl]
	    [cemerick.pomegranate :as pom]
	    [dj.io]
            [clojure.string])
  (:refer-clojure :exclude (add-classpath)))

(defn reload-class-file
  "Reload a .class file during runtime, this allows you to recompile
  java components, and reload their class files to get the updated
  class definitions"
  [path]
  (let [f (dj.io/file path)
	classname (second (re-matches #"(\w+)\.class" (.getName f)))]
    (.defineClass (clojure.lang.DynamicClassLoader.)
		  classname
		  (dj.io/to-byte-array f)
		  nil)))

(defn reset-native-paths! [native-paths]
  ;; Reset java.library.path by setting sys_paths variable in
  ;; java.lang.ClassLoader to NULL, depends on java implementation
  ;; knowledge
  (let [clazz java.lang.ClassLoader
	field (.getDeclaredField clazz "sys_paths")]
    (.setAccessible field true)
    (.set field clazz nil)
    (System/setProperty "java.library.path" (apply str (interpose (if (re-find #"(?i)windows"
									       (System/getProperty "os.name"))
								    ";"
								    ":")
								  native-paths)))))

(defn append-native-path! [new-paths]
  (let [previous-paths (clojure.string/split (System/getProperty "java.library.path")
					     #":|;")]
    (reset-native-paths! (concat previous-paths new-paths))))

(defn resource-as-stream ^java.io.InputStream [str-path]
  (let [cl ^java.lang.ClassLoader (first (pom/classloader-hierarchy))]
    (.getResourceAsStream cl str-path)))

(defn resource-as-str [str-path]
  (let [is (resource-as-stream str-path)]
    (if is
      (apply str (map char (take-while #(not= % -1) (repeatedly #(.read is)))))
      (throw (ex-info "str-path does not resolve" (dj.repl/local-context))))))

(defn load-resource [str-path]
  (-> str-path
      resource-as-str
      read-string
      eval))

(defn find-resource [relative-path]
  (-> relative-path
      pom/resources
      first
      (.getPath)
      dj.io/file))
