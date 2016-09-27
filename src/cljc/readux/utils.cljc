(ns readux.utils)

(defmacro log-group
  [label & body]
  `(let [grp# ~label]
     (.group js/console grp#)
     (let [val# (do ~@body)]
       (.groupEnd js/console grp#)
       val#)))

(defmacro log-group-collapsed
  [label & body]
  `(let [grp# ~label]
     (.groupCollapsed js/console grp#)
     (let [val# (do ~@body)]
       (.groupEnd js/console grp#)
       val#)))