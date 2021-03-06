(ns pluggable-web.pl-routing.core
  (:require [reagent.core :as r]
            [reitit.frontend :as rf]
            [reitit.frontend.easy :as rfe]
            [pluggable-web.pl-template.core :as template]
            [pluggable-web.pl-spa.core :as spa]))

(defn debug [& args]
  (println "==============================================")
  (apply println args)
  (println "=============================================="))

(defprotocol IRouter
  (-href [_ route-id params query])
  (-goto [_ route-id params query])
  (go-home [_]))

(defn goto
  ([this route_id params query] (-goto this route_id params query))
  ([this route_id params] (goto this route_id params {}))
  ([this route_id ] (goto this route_id {})))

(defn href
  ([this route_id params query] (-href this route_id params query))
  ([this route_id params] (href this route_id params {}))
  ([this route_id ] (href this route_id {})))

(defprotocol IInternalRouter
  (-current-route [_]))

(defn- ui-no-page-defined [router]
  [:h1 "Error: no page has been defined"])

(defn display-error [e]
  [:div
   {:style
    {:padding "2em 3em 2em 3em"
     :margin :2em
     :border-radius :0.8em
     :border "1px solid red"
     :background :#fff6f6}}
   [:h1 [:i.ui.bug.icon] "Oops! You have found a bug..."]
   [:p
     "There is a bug in the implementation of this page. "
     "The internal error message is:"]
   [:p {:style {:padding :1em
                :color :#965
                :border-left "4px solid #A87"}}
    (str e)]])

(defn- current-page [router template]
  (let [curr @(-current-route router)
        curr (if-not curr
               (do
                 (go-home router)
                 @(-current-route router))
               curr)]
    [template
     [:div
      [:div
       (if curr
         (let [view (:view (:data curr))]
           (try
             (cond
               (vector? view) view
               :else (view (:parameters curr)))
             (catch js/Error e [display-error e])))
         [ui-no-page-defined router])]]]))

(defn- ui-home-page []
  [:div
   [:h1 "This is the homepage"]
   [:p (str "No content has been defined. You need to set the app main page"
              " by defining a ::pluggable-web.routing.core/home-page extension")]
   [:p
    [:a {:href (rfe/href ::about-page)} "Go to test about page"]]])

(defn- about-page []
  [:div
   [:h1 "About"]
   [:div "This is the test about page"]
   [:div
    [:a {:href (rfe/href ::home-page)} "Return to home page"]]])

(defn- init! [routes set-route]
  (rfe/start!
   (rf/router routes)
   set-route
   {:use-fragment true})) ;; set to false to enable HistoryAPI

(defonce current-route (r/atom nil))
(defonce on-route-change-handlers (r/atom []))

(defn- init-router [routes]
  (init!
   routes
   (fn [m]
     (reset! current-route m)
     (doall (map (fn [h] (when (fn? h) (h))) @on-route-change-handlers)))))

(defn- create-router []
  (reify
    IRouter
    (-href [_ route-id params query] (rfe/href route-id params query))
    (-goto [_ route-id params query] (rfe/push-state route-id params query))
    (go-home [this] (goto this ::home-page))

    IInternalRouter
    (-current-route [_] current-route)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Extension handlers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- create-bean [kw name props]
  (let [key (keyword (str ::routes "--" kw))
        val [(fn [b] (vector name (assoc props :view b))) kw]]
    {:key key :val val}))

(defn- add-route-to-db [db r] ;; FIXME: this would be simpler if used inner beans
  (let [h     (first r)
        props (second r)
        view  (:view props)]
    (cond
      (keyword? view)
      (let [bean (create-bean view h props)]
        (-> db
            (assoc-in [:beans (:key bean)] (:val bean))
            (update-in [:beans ::routes] #(conj (or % [vector]) (:key bean)))))

      :else
      (update-in db [:beans ::routes] #(conj (or % [vector]) r)))))

(defn ext-handler-home-page [db vals]
  (add-route-to-db
   db
   ["/home"
    {:name ::home-page
     :view (last vals)}]))

(defn- add-routes-to-db [db routes]
  (reduce add-route-to-db db routes))

(defn ext-handler-routes [db vals]
  (reduce add-routes-to-db db vals))

(def plugin
  {:id         ::routing
   :beans      {::router        {:constructor [#'create-router]
                                 :mutators    [[#'init-router ::routes]]}
                ::about-page    about-page}
   :extensions [{:key     ::home-page
                 :handler ext-handler-home-page
                 :doc     "Fixes the home page (route) of the application,
                           replacing any previously defined home page"}
                {:key     ::routes
                 :handler ext-handler-routes
                 :doc     "Adds the given list of routes to the routes of the application"}]

   ::routes    [["/about"
                 {:name ::about-page
                  :view ::about-page}]]
   ::home-page #'ui-home-page
   ::spa/main-component [vector #'current-page ::router ::template/ui-page-template]})
