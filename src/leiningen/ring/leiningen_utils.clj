(ns leiningen.ring.leiningen-utils
  (:require 
    [clojure.java.io :as io]
    [leiningen.core.classpath :as classpath]
    ))

;; - copied directly from leiningen classpath
;;   these are declared private there but are necessary here

;; Basically just for re-throwing a more comprehensible error.
(defn read-dependency-project [root dep]
  (let [project-file (io/file root "checkouts" dep "project.clj")]
    (if (.exists project-file)
      (let [project (.getAbsolutePath project-file)]
        ;; TODO: core.project and core.classpath currently rely upon each other *uk*
        (require 'leiningen.core.project)
        (try ((resolve 'leiningen.core.project/read) project [:default])
             (catch Exception e
               (throw (Exception. (format "Problem loading %s" project) e)))))
      (println
       "WARN ignoring checkouts directory" dep
       "as it does not contain a project.clj file."))))

(defn normalize-path [root path]
  (let [f (io/file path)] ; http://tinyurl.com/ab5vtqf
    (.getAbsolutePath (if (or (.isAbsolute f) (.startsWith (.getPath f) "\\"))
                        f (io/file root path)))))

;; end copied directly from leiningen classpath


(defn classpath-dirs 
  "list of all dirs on the leiningen classpath"
  [project]
  (filter #(.isDirectory (io/file %)) 
          (classpath/get-classpath project)))

(defn checkout-test-paths
  "extract the checkout test paths for the project
     based on leiningen.classpath/checkout-deps-paths
  "
  [project]
  (apply concat (for [dep (.list (io/file (:root project) "checkouts"))
                      :let [dep-project (read-dependency-project
                                          (:root project) dep)]
                      :when dep-project]
                  (:test-paths dep-project))))

(defn get-test-paths
  "Return the test paths for project (including checkouts) as a list of strings.
    based on leinigen.classpath/get-classpath"
  [project]
  (for [path (concat 
               (:test-paths project)
               (checkout-test-paths project))
        :when path]
    (normalize-path (:root project) path)))

(defn classpath-dirs-excluding-testpaths
  "classpath with test-paths removed"
  [project]
  (remove (set (get-test-paths project))
          (classpath-dirs project)))
