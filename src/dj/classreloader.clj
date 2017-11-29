(ns dj.classreloader
  (:import [java.net URISyntaxException])
  (:require [dj]
            [dj.repl]
	    [cemerick.pomegranate :as pom]
	    [dj.io]
            [clojure.string]))

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

(defn reset-native-paths!
  "won't work in Java 9+ because of the removal of setAccessible. See
  this stackoverflow answer for a new implementation strategy.

  https://stackoverflow.com/questions/42052856/java-9-classpath-and-library-path-extension

  public class MiscTools
{
    private static class SpclClassLoader extends URLClassLoader
    {
        static
        {
            ClassLoader.registerAsParallelCapable();
        }

        private final Set<Path> userLibPaths = new CopyOnWriteArraySet<>();

        private SpclClassLoader()
        {
            super(new URL[0]);
        }

        @Override
        protected void addURL(URL url)
        {
            super.addURL(url);
        }

        protected void addLibPath(String newpath)
        {
            userLibPaths.add(Paths.get(newpath).toAbsolutePath());
        }

        @Override
        protected String findLibrary(String libname)
        {
            String nativeName = System.mapLibraryName(libname);
            return userLibPaths.stream().map(tpath -> tpath.resolve(nativeName)).filter(Files::exists).map(Path::toString).findFirst().orElse(super.findLibrary(libname));            }
    }
    private final static SpclClassLoader ucl = new SpclClassLoader();

    /**
     * Adds a jar file or directory to the classpath. From Utils4J.
     *
     * @param newpaths JAR filename(s) or directory(s) to add
     * @return URLClassLoader after newpaths added if newpaths != null
     */
    public static ClassLoader addToClasspath(String... newpaths)
    {
        if (newpaths != null)
            try
            {
                for (String newpath : newpaths)
                    if (newpath != null && !newpath.trim().isEmpty())
                        ucl.addURL(Paths.get(newpath.trim()).toUri().toURL());
            }
            catch (IllegalArgumentException | MalformedURLException e)
            {
                RuntimeException re = new RuntimeException(e);
                re.setStackTrace(e.getStackTrace());
                throw re;
            }
        return ucl;
    }

    /**
     * Adds to library path in ClassLoader returned by addToClassPath
     *
     * @param newpaths Path(s) to directory(s) holding OS library files
     */
    public static void addToLibraryPath(String... newpaths)
    {
        for (String newpath : Objects.requireNonNull(newpaths))
            ucl.addLibPath(newpath);
    }
  }

  Also add this at the start
  Thread.currentThread().setContextClassLoader(MiscTools.addToClasspath());

  For extended classpaths
  try
{
    Class.forName(classname, true, MiscTools.addToClasspath(cptoadd);
}
catch (ClassNotFoundException IllegalArgumentException | SecurityException e)
{
    classlogger.log(Level.WARNING, \"Error loading \".concat(props.getProperty(\"Class\")), e);
}

  How to override classloader identification checks
private final static CopyOnWriteArraySet<Driver> loadedDrivers = new CopyOnWriteArraySet<>();

private static Driver isLoaded(String drivername, String... classpath) throws ClassNotFoundException
{
    Driver tdriver = loadedDrivers.stream().filter(d -> d.getClass().getName().equals(drivername)).findFirst().orElseGet(() ->
    {
        try
        {
            Driver itdriver = (Driver) Class.forName(drivername, true, addToClasspath(classpath)).newInstance();
            loadedDrivers.add(itdriver);
            return itdriver;
        }
        catch (ClassNotFoundException | IllegalAccessException | InstantiationException e)
        {
            return null;
        }
    });
    if (tdriver == null)
        throw new java.lang.ClassNotFoundException(drivername + \" not found.\");
    return tdriver;
}
  "
  [native-paths]
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
