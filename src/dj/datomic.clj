(ns dj.datomic
  (:require [cemerick.pomegranate :as pom]
            [dj]
            [dj.io]))

(defn download []
  "goto http://downloads.datomic.com/free.html and download the newest version of datomic")

(defn install [datomic-zip-file tmp-dir]
  "installs the downloaded version of datomic in local maven repository"
  (let [folder-name (dj/substring (dj.io/get-name datomic-zip-file)
                                  0
                                  -4)
        content-folder (dj.io/file tmp-dir folder-name)
        [_ name version] (re-matches #"(datomic-free)-(.+)"
                                     folder-name)
        pom-file (dj.io/file content-folder "pom.xml")]
    (dj.io/unzip datomic-zip-file
                 tmp-dir)
    (cemerick.pomegranate.aether/install :coordinates ['com.datomic/datomic-free version]
                                         :jar-file (dj.io/file content-folder (str folder-name ".jar"))
                                         :pom-file pom-file)))

