(ns readux.utils)

(defmacro with-console-group
  [label & body]
  `(let [grp# ~label]
     (.group js/console grp#)
     (let [val# (do ~@body)]
       (.groupEnd js/console grp#)
       val#)))