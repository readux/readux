(set-env!
  :resource-paths #{"public"}
  :source-paths #{"src/cljs" "src/cljc"}

  :dependencies '[[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.227"]
                 [reagent "0.6.0-rc"]
                 [readux "0.1.5-SNAPSHOT"]
                 [adzerk/boot-cljs "1.7.228-1" :scope "test"]
                 [pandeiro/boot-http "0.7.3" :scope "test"]
                 [adzerk/boot-reload "0.4.12" :scope "test"]
                 [adzerk/boot-cljs-repl "0.3.3" :scope "test"]
                 [com.cemerick/piggieback "0.2.1" :scope "test"]
                 [weasel "0.7.0" :scope "test"]
                 [org.clojure/tools.nrepl "0.2.12" :scope "test"]]

  :checkouts '[[readux "0.1.5-SNAPSHOT"]]
  :project 'readux-todo-example
  :version "0.1.1-SNAPSHOT")

(require '[adzerk.boot-cljs :refer [cljs]]
         '[pandeiro.boot-http :refer [serve]]
         '[adzerk.boot-reload :refer [reload]]
         '[adzerk.boot-cljs-repl :refer [cljs-repl start-repl]])

(task-options!
  pom {:project (get-env :project)
       :version (get-env :version)
       :license {"MIT" "https://mit-license.org/"}
       :url "https://readux.github.io"
       :description "Example TODO application using readux"}
  cljs-repl {:nrepl-opts {:port 9000}})

(deftask dev
  "Launch immediate feedback development environment"
  []
  (comp (serve :dir "target")
        (watch)
        (reload)
        (cljs-repl)
        (cljs)
        (target :dir #{"target"})))