(ns leiningen.ring.server
  (:require [leinjacker.deps :as deps]
            [leiningen.ring.leiningen-utils :as lu])
  (:use [leinjacker.eval :only (eval-in-project)]
        [leiningen.ring.util :only (ensure-handler-set! update-project)]))

(defn load-namespaces
  "Create require forms for each of the supplied symbols. This exists because
  Clojure cannot load and use a new namespace in the same eval form."
  [& syms]
  `(require
    ~@(for [s syms :when s]
        `'~(if-let [ns (namespace s)]
             (symbol ns)
             s))))

(defn reload-paths [project]
  (or (get-in project [:ring :reload-paths])
      (lu/classpath-dirs-excluding-testpaths project)))

(defn add-dep [project dep]
  (update-project project deps/add-if-missing dep))

(defn add-server-dep [project]
  (add-dep project '[ring-server/ring-server "0.2.8"]))

(defn start-server-expr [project]
  `(ring.server.leiningen/serve '~(select-keys project [:ring])))

(defn nrepl? [project]
  (-> project :ring :nrepl :start?))

(defn add-optional-nrepl-dep [project]
  (if (nrepl? project)
    (add-dep project '[org.clojure/tools.nrepl "0.2.2"])
    project))

(defn start-nrepl-expr [project]
  (let [port (-> project :ring :nrepl (:port 0))]
    `(let [{port# :port} (clojure.tools.nrepl.server/start-server :port ~port)]
       (println "Started nREPL server on port" port#))))

(defn server-task
  "Shared logic for server and server-headless tasks."
  [project options]
  (ensure-handler-set! project)
  (let [project (-> project
                    (assoc-in [:ring :reload-paths] (reload-paths project))
                    (update-in [:ring] merge options))]
    (println "Reload-paths"  (with-out-str (clojure.pprint/pprint (reload-paths project))))
    (println "test-paths"  (with-out-str (clojure.pprint/pprint (lu/get-test-paths project))))
     
    (eval-in-project
     (-> project add-server-dep add-optional-nrepl-dep)
     (if (nrepl? project)
       `(do ~(start-nrepl-expr project) ~(start-server-expr project))
       (start-server-expr project))
     (load-namespaces
      'ring.server.leiningen
      (if (nrepl? project) 'clojure.tools.nrepl.server)
      (-> project :ring :handler)
      (-> project :ring :init)
      (-> project :ring :destroy)))))

(defn server
  "Start a Ring server and open a browser."
  ([project]
     (server-task project {}))
  ([project port]
     (server-task project {:port (Integer. port)})))
