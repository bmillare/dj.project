(ns dj.dependencies
  (:require [cemerick.pomegranate :as pom]
	    [dj]
	    [dj.io]))

(defn add-dependencies
  "like pomegranate's add-dependencies, supply leiningen style vector of dependency specifications, aka
 [org.clojure/clojure \"1.5.1\"] ...

system must have key :dj/repositories
"
  [coordinates system]
  (pom/add-dependencies :coordinates coordinates
                        :repositories (:dj/repositories system)))

(defn eval-project-form
  "convert defproject form into a hash-map"
  [project-form]
  (apply hash-map (rest project-form)))

(defmulti resolve-dj-dependency (fn [entry-obj _]
                                  (:dependency-type entry-obj)))

(defn parse-dj-project-dependency [entry]
  (if (= java.lang.String (type entry))
    (let [components (.split #"/" entry)]
      (if (re-find #"http://|git://|https://|ssh://" entry)
	(let [[_ n] (re-find #"((?:\w|-|_|\.)+)" (last components))]
	  {:dependency-type :git
	   :name (if (= (dj/substring n -4)
                        ".git")
                   (dj/substring n 0 -4)
                   n)
	   :git-path entry})
	(if (= "clojure" (first components))
	  {:dependency-type :git
	   :name entry
	   :git-path (str "git://github.com/" entry ".git")}
	  {:dependency-type :source
	   :name (last components)
	   :relative-path entry})))
    entry))

(defn resolve-path [path system]
  (let [f (dj.io/file path)]
    (if (.isAbsolute f)
      f
      (dj.io/file (:dj/src-path system) path))))

(defn resolve-project [path system]
  (let [project-dir (resolve-path path system)
        project-file (dj.io/file project-dir "project.clj")
	project-data (-> project-file
			 slurp
			 read-string
			 eval-project-form
			 (assoc :root (dj.io/get-path project-dir)))
        dependencies (:dependencies project-data)
        project-source-paths (:source-paths project-data)
        source-paths (if (empty? project-source-paths)
                       [(dj.io/file project-dir "src")]
                       project-source-paths)]
    (doall (map pom/add-classpath source-paths))
    (when-let [dj-dependencies (:dj/dependencies project-data)]
      (doall (map (fn [d]
                    (-> d
                        parse-dj-project-dependency
                        (resolve-dj-dependency system)))
                  dj-dependencies)))
    (pom/add-dependencies :coordinates dependencies
                          :repositories (merge cemerick.pomegranate.aether/maven-central
                                               {"clojars" "http://clojars.org/repo"}))))
;; we want to be able to resolve a project, then we can learn to resolve a git repo

;; keywords might clash, one potential fix is to used namespaced
;; keywords, i can guarantee that all the keywords are not fully
;; qualified

;; non-jgit installed version
(defmethod resolve-dj-dependency :git [entry-obj system]
	   (let [f (resolve-path (:name entry-obj) system)]
             (when-not (dj.io/exists? f)
	       (throw (Exception. "git repository not found")))
	     (resolve-project (dj.io/get-path f) system)))

;; i don't want to fully commit to "usr/src"
;; i'd like a verbose mode, where a map is passed
(defmethod resolve-dj-dependency :source [entry-obj system]
	   (let [relative-path (:relative-path entry-obj)
		 f (if relative-path
		     (resolve-path relative-path system)
		     (dj.io/file (:root-path entry-obj)))]
	     (resolve-project (dj.io/get-path f) system)))

(defn project-source-dependencies* [system input:queue input:return input:visited]
  ((fn project-source-dependencies** [dependency:queue dependency:return dependency:visited]
     (let [d (first dependency:queue)]
       (if d
         (if (dependency:visited (:name d))
           (project-source-dependencies** (rest dependency:queue)
                                          dependency:return
                                          dependency:visited)
           (let [dependency:type (:dependency-type d)
                 project-file (-> ((case dependency:type
                                     :git :name
                                     :source :relative-path) d) 
                                  (resolve-path system)
                                  (dj.io/file "project.clj"))
                 dependency:expansion (-> project-file
                                          slurp
                                          read-string
                                          eval-project-form
                                          :dj/dependencies
                                          (->> (map parse-dj-project-dependency)))]
             (project-source-dependencies** (concat dependency:expansion
                                                    (rest dependency:queue))
                                            (conj dependency:return
                                                  d)
                                            (conj dependency:visited
                                                  (:name d)))))
         dependency:return)))
   input:queue input:return input:visited))

(defn project-source-dependencies
  "return a list of dependencies that are from source, git or local"
  [relative-path system]
  (project-source-dependencies* system
                                (list (parse-dj-project-dependency relative-path))
                                []
                                #{}))
