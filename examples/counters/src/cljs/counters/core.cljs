(ns counters.core
  (:require [reagent.core :as r]
            [reagent.ratom :refer [reaction]]
            [readux.core :as rdc :include-macros true]
            [readux.store :as rds]))

(enable-console-print!)

;; reducers
;; --------
;; A reducer essentially consists of two parts:
;;   1. some initial state
;;   2. some actions which can mutate that state 
;;      (a map of actions or 'action map' for short)

;; NOTE - could rewrite map of actions to:
;; ---
;; (rdc/actions
;;  {:incr (update model :counter inc)
;;   :decr (update model :counter dec)})
;; ---
;;
;; rdc/actions implicitly wrap every value in a (fn [model action] ...) form
(def counter-actions
  {:incr (fn [model action] (update model :counter inc))
   :decr (fn [model action] (update model :counter dec))})

;; About as simple as a reducer can be - we must handle nil input.
(defn num-actions-reducer
  [m a]
  (let [m (or m 1)]
    (inc m)))

;; Note how the structure of the reducer must match the paths
;; to the counters in the state tree defined further down
;; ([:counters :1] and [:counters :2])
;;
;; And note how we define :ctx to prefix the actions
;; from the action map with the same NS' we use further down when
;; we create two separate instances of the counter.
(def app-reducer
  (rdc/composite-reducer
    {:counters
     {:1 {:init {:counter 11}
          :actions counter-actions
          :ctx :counter1}
      :2 {:init {:counter 20}
          :actions counter-actions
          :ctx :counter2}}
     :num-actions num-actions-reducer}))

;; store
;; -----
(defonce store (rdc/store app-reducer (rds/apply-mw rds/log-model-diff)))

;; queries
;; -------
(defn- counter-value-query
  [model [query-id]]
  (assert (= query-id :counter-value))
  (reaction (:counter @model)))

(defn- num-actions-query
  [model [query-id]]
  (assert (= query-id :num-actions))
  (reaction (:num-actions @model)))

;; presentational components
;; -------------------------
(defn counter
  [value on-inc on-dec]
  [:div {:display :inline}
   [:button {:style {:display :inline :margin "3px"} :on-click on-dec} "-"]
   [:button {:style {:display :inline :margin "3px"} :on-click on-inc} "+"]
   [:p {:style {:display :inline :margin-left "10px"}} @value]])

;; control components
;; ------------------
(defn counter-ctrl
  [dispatch query]
  ;; These queries and actions are all without a NS (i.e. contextual)
  ;; - if this component is 'connect'ed, they will be working within
  ;; the supplied context.
  (let [value (query [:counter-value])
        on-inc #(dispatch {:type :incr})
        on-dec #(dispatch {:type :decr})]
    [counter value on-inc on-dec]))

(defn- app
  [store]
  ;; Connect controller to a store, a namespace and a path into the state-tree.
  ;;
  ;; The 'ns' is used to prefix action dispatches which have no NS already,
  ;; (i.e. actions relative to a context). This ensures multiple instances of
  ;; counters can dispatch their actions without interfering with one another.
  ;;
  ;; The path within the state-tree gives each counter a place to store its
  ;; state, again to avoid interference.
  ;;
  ;; actions whose ':type' and queries whose query-id have a NS set are
  ;; not relative to a context, but operate on the global state. That is,
  ;; they are not impacted by 'connect'.
  (let [counter1 (rdc/connect counter-ctrl store :counter1 [:counters :1])
        counter2 (rdc/connect counter-ctrl store :counter2 [:counters :2])
        num-actions (rdc/query store [:num-actions])]
    (fn app-render []
      [:div
       [counter1]
       [counter2]
       [:p (str "processed '" @num-actions "' actions so far.")]])))

(defn page []
  (rdc/query-reg! store :counter-value counter-value-query)
  (rdc/query-reg! store :num-actions num-actions-query)
  (fn page-render []
    [:div.container
     [:div.jumbotron
      [:h1 "counters"]
      [:p "Welcome to counters"]
      [app store]]]))

(r/render-component [page]
                    (. js/document (getElementById "app")))