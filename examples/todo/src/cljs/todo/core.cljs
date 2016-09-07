(ns todo.core
  (:require [reagent.core :as r]
            [reagent.ratom :refer [reaction]]
            [readux.core :as rdc :include-macros true]
            [readux.middleware.log-model-diff :refer [log-model-diff]]))

(enable-console-print!)

;; Reducers
;; --------
(def todos-reducer
  (rdc/reducer-fn
    [model action data]
    {:todos        []
     :next-todo-id 0}
    {:ADD-TODO
     (let [text (first data)
           next-id (:next-todo-id model)]
       (if (not-empty text)
         (-> model
             (update :todos
                     #(conj %1 {:id        next-id
                                :text      text
                                :completed false}))
             (update :next-todo-id inc))
         model))
     :TOGGLE-TODO
     (let [todo-id (first data)]
       (->> #(map (fn [todo]
                    (if (= (:id todo) todo-id)
                      (update todo :completed not)
                      todo)) %1)
            (update model :todos)))}))

(def filter-reducer
  (rdc/reducer-fn
    [model action data]
    "SHOW_ALL"
    {:SET-VISIBILITY-FILTER
     (first data)}))

(def app-reducer
    (->
      (rdc/composite-reducer {:todos  todos-reducer
                              :filter filter-reducer})
      log-model-diff))

(defonce store (rdc/store app-reducer))

(defn prevent-default
  [cb]
  (fn prevent-default [event]
    (.preventDefault event)
    (cb event)))

;; Presentational Components
;; -------------------------
(defn todo
  [text completed on-click]
  [:li {:on-click (prevent-default on-click)
        :style {:text-decoration (if completed "line-through" "none")}}
   text])

(defn todo-list
  [todos on-todo-click]
  [:ul
   (for [item todos]
     ^{:key item} [todo (:text item)
                   (:completed item)
                   (on-todo-click (:id item))])])

(defn link
  [label active on-click]
  (if active
    [:span label]
    [:a {:href "#" :on-click (prevent-default on-click)} label]))

(defn footer
  [filter-link]
  (fn footer-render []
    [:p "Show " [filter-link {:filter "SHOW_ALL"} "All"]
     ", " [filter-link {:filter "SHOW_ACTIVE"} "Active"]
     ", " [filter-link {:filter "SHOW_COMPLETED"} "Completed"]]))

;; Queries
;; -------
(defn- filter-todos
  [todos current-filter]
  (case current-filter
    "SHOW_ALL" todos
    "SHOW_ACTIVE" (filter #(false? (:completed %1)) todos)
    "SHOW_COMPLETED" (filter #(true? (:completed %1)) todos)))

(defn- visible-todos-query
  [model [query-id]]
  (assert (= query-id :visible-todos))
  ;; Using a chained reaction to avoid filtering on each model change
  (let [todos (reaction (get-in @model [:todos :todos]))
        filter (reaction (:filter @model))]
    (reaction (filter-todos @todos @filter))))

(defn- current-filter-query
  [model [query-id]]
  (assert (= query-id :current-filter))
  (reaction (:filter @model)))


;; Container Components
;; --------------------
(defn visible-todo-list
  [store]
  (let [todos (rdc/query store [:visible-todos])
        toggle-todo (fn [id] #(rdc/dispatch store :TOGGLE-TODO id))]
    (fn visible-todo-list-render []
      [todo-list @todos toggle-todo])))

(defn filter-link
  [store]
  (let [current-filter (rdc/query store [:current-filter])]
    (fn filter-link-render [{:keys [filter]} label]
      [link label (= filter @current-filter)
       #(rdc/dispatch store :SET-VISIBILITY-FILTER filter)])))

(defn add-todo
  [store]
  (let [input (r/atom "")]
    (fn add-todo-render []
      [:div
       [:input {:type "text"
                :value @input
                :on-change #(reset! input (-> % .-target .-value))}]
       [:button {:on-click #(do (rdc/dispatch store :ADD-TODO @input)
                                (reset! input ""))} "Save"]])))

;; Finally, the app
(defn- app
  [store]
  (fn app-render []
    [:div
     [add-todo store]
     [visible-todo-list store]
     [footer (filter-link store)]]))

(defn page []
  (rdc/query-reg! store :visible-todos visible-todos-query)
  (rdc/query-reg! store :current-filter current-filter-query)
  (fn page-render []
    [:div.container
     [:div.jumbotron
      [:h1 "Todo App"]
      [:p "Example todo-app"]
      [app store]]]))

;; define your app data so that it doesn't get over-written on reload


(r/render-component [page]
  (. js/document (getElementById "app")))

