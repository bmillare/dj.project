(defproject org.bmillare/dj.project "0.3.2"
  :description "dj dynamic utilities, load jars, load native jars, reload jars, and load projects via project.clj files on the fly"
  :url "https://github.com/bmillare/dj.project"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.bmillare/dj.core "0.1.0"]
                 [com.cemerick/pomegranate "0.3.0"]]
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.5.1"]]}})
