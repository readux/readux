(ns readux.utils
  (:require [cljs.pprint :refer [pprint]]))

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