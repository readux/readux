(ns readux.utils
  (:require [cljs.pprint :refer [pprint]]
            [clojure.string :refer [starts-with? trim-newline]]
            [clojure.spec :as s])
  (:require-macros [readux.utils :refer [log-group log-group-collapsed]]))

(defn ppstr
  [obj]
  (with-out-str (cljs.pprint/pprint obj)))

(defn log
  [& args]
  (.apply (.-log js/console) js/console (into-array args)))

(defn warn
  [& args]
  (.apply (.-warn js/console) js/console (into-array args)))

(defn error
  [& args]
  (.apply (.-error js/console) js/console (into-array args)))

(defn spec-valid?
  ([spec val] (spec-valid? spec val nil))
  ([spec val msg]
   (let [out (with-out-str (s/explain spec val))]
     (if (starts-with? out "Success")
       true
       (do
         (log-group-collapsed
           (str "Spec '" (-> spec ppstr trim-newline) "' failed check")
           (when msg
             (log-group
               "Message" (warn msg)))
           (log-group
             "Spec" (warn (ppstr spec)))
           (log-group
             "Value" (warn (ppstr val)))
           (log-group
             "Error" (warn (ppstr out))))
           false)))))