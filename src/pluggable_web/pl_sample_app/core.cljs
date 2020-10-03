(ns pluggable-web.pl-sample-app.core
  (:require [pluggable-web.pl-routing.core :as routing]
            [pluggable-web.pl-template.core :as template]))

(defn on-home-click [router]
  (fn [] (routing/go-home router)))

(def plugin
  {:id                      ::sample-app
   :beans                   {::on-logo-click [on-home-click ::routing/router]}
   ::template/on-logo-click ::on-logo-click

   ::template/app-icon [:= [:i.ui.circle.icon]]
   ::template/app-name [:= [:div "Sample app"]]})

