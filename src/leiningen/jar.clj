(ns leiningen.jar
  "Create a jar containing the compiled code and original source."
  (:require [leiningen.compile :as compile])
  (:use [leiningen.pom :only [make-pom make-pom-properties]]
        [clojure.contrib.duck-streams :only [to-byte-array copy]]
        [clojure.contrib.str-utils :only [str-join re-sub]]
        [clojure.contrib.java-utils :only [file]])
  (:import [java.util.jar Manifest JarEntry JarOutputStream]
           [java.io BufferedOutputStream FileOutputStream
            ByteArrayInputStream]))

(defn make-manifest [project]
  (Manifest.
   (ByteArrayInputStream.
    (to-byte-array
     (str  (str-join "\n"
                     ["Manifest-Version: 1.0" ; DO NOT REMOVE!
                      "Created-By: Leiningen"
                      (str "Built-By: " (System/getProperty "user.name"))
                      (str "Build-Jdk: " (System/getProperty "java.version"))
                      (when-let [main (:main project)]
                        (str "Main-Class: " main))])
           "\n")))))

(defmulti copy-to-jar (fn [project jar-os spec] (:type spec)))

(defmethod copy-to-jar :path [project jar-os spec]
  (doseq [child (file-seq (file (:path spec)))]
    (when-not (.isDirectory child)
      (let [path (str child)
            path (re-sub (re-pattern (str "^" (:root project))) "" path)
            path (re-sub #"^/classes" "" path)
            path (re-sub #"^/src" "" path)
            path (re-sub #"^/" "" path)]
        (.putNextEntry jar-os (JarEntry. path))
        (copy child jar-os)))))

(defmethod copy-to-jar :bytes [project jar-os spec]
  (.putNextEntry jar-os (JarEntry. (:path spec)))
  (copy (ByteArrayInputStream. (:bytes spec)) jar-os))

(defn write-jar [project out-filename filespecs]
  (with-open [jar-os (JarOutputStream. (BufferedOutputStream.
                                        (FileOutputStream. out-filename))
                                       (make-manifest project))]
    (doseq [filespec filespecs]
      (copy-to-jar project jar-os filespec))))

(defn jar
  "Create a $PROJECT.jar file containing the compiled .class files as well as
the source .clj files. If project.clj contains a :main symbol, it will be used
as the main-class for an executable jar."
  ([project jar-name]
     (compile/compile project)
     (let [jar-file (str (:root project) "/" jar-name)
           filespecs [{:type :bytes
                       :path (format "meta-inf/maven/%s/%s/pom.xml"
                                     (:group project)
                                     (:name project))
                       :bytes (make-pom project)}
                      {:type :bytes
                       :path (format "meta-inf/maven/%s/%s/pom.properties"
                                     (:group project)
                                     (:name project))
                       :bytes (make-pom-properties project)}
                      {:type :path :path *compile-path*}
                      {:type :path :path (str (:root project) "/src")}
                      {:type :path :path (str (:root project) "/project.clj")}]]
       ;; TODO: support slim, etc
       (write-jar project jar-file filespecs)
       jar-file))
  ([project] (jar project (str (:name project) ".jar"))))
