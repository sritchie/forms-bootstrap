(ns forms-bootstrap.core
  (:use net.cgrand.enlive-html
        noir.core
        forms-bootstrap.util
        [sandbar.validation :only (if-valid)])
  (:require [noir.validation :as vali]
            [clojure.string :as string]
            [noir.session :as session]
            [noir.response :as response]))

(def form-template "forms_bootstrap/forms-template.html")
;;HELPER FUNCTIONS

(defn handle-error-css
  "Used within a snippet to add 'error' to the class of an html element, if appropriate."
  [errors]
  (if (peek errors)
     (add-class "error")
     identity))

(defn span
  "Creates an enlive node map for an html span element. Type should be
  'help-inline' or 'help-block.'"
  [type content]
  {:tag "span"
   :attrs {:class type}
   :content content})

(defn add-spans
  "Used by enlive snippets to add an inline span to display errors (if
  there are any). If there are no errors then it adds any inline or
  block help messages."
  [errors help-inline help-block]
  (if (peek errors)
    (append (span "help-inline" (peek errors)))
    (do->
     (if (seq help-inline)
       (append (span "help-inline" help-inline))
       identity)
     (if (seq help-block)
       (append (span "help-block" help-block))
       identity))))


;; SNIPPETS

;;Grabs the whole form from the template, sets the action, replaces
;;the entire contents with fields, and appends a submit button.
;;Fields here is a sequence of stuff generated by all of the
;;defsnippets below, one for each form element
(defsnippet basic-form
  form-template
  [:form]
  [{:keys [action fields submitter class enctype legend method form-attrs]
    :or {class "form-horizontal"
         method "post"}}]
  [:form] (do-> (apply set-attr (concat [:action action :class class :method method]
                                        (flatten (into [] form-attrs))))
                (if (seq enctype)
                  (set-attr :enctype enctype)
                  identity)
                (content (if (empty? legend)
                           fields
                           (list {:tag :legend
                                  :content legend}
                                 fields)))
                (append submitter)))

;;Creates a hidden input field
(defsnippet hidden-input
  form-template
  [:div.hidden-field :input]
  [{:keys [id class hidden name errors default disabled value]
    :or {size "input-large"
         default ""}}]
  [:input] (do->
            (set-attr :name name)
            (if value
              (set-attr :value value)
              (set-attr :value default))
            (if (= disabled true)
              (set-attr :disabled "")
              identity)
            (add-class class)))

;; Generates a txt or password input form field for a given map.
;; inputs: name label type size default errors
;; ex: [:name "namehere" :label "labelhere" :type "text"
;;      :size "xlarge" :errors ["some error"] :default "john"]
(defsnippet input-lite
  form-template
  [:div.input-field :input]
  [{:keys [id name label class type size errors default
           disabled placeholder value onclick style custom-attrs]
    :or {size "input-large"
         default ""}}]
  [:input] (do->
            (set-attr :name name :type type :class size :style style :placeholder placeholder
                      :id id)
            (if custom-attrs
              (apply set-attr custom-attrs)
              identity)
            (if value
              (set-attr :value value)
              (set-attr :value default))
            (if (seq onclick)
              (set-attr :onclick onclick)
              identity)
            (if (= disabled true)
              (set-attr :disabled "")
              identity)
            (add-class class)))

(defsnippet input-field
  form-template
  [:div.input-field]
  [{:keys [id hidden name label class type size errors default value disabled
           placeholder help-inline help-block]
    :as m}]
  [:.input-field] (do->
                   (if id
                     (set-attr :id id)
                     identity)
                   (if hidden
                     (add-class "hidden")
                     identity)
                   (handle-error-css errors))
  [:label] (do-> (content label)
                 (set-attr :for name))
  [:div.controls :input] (constantly (input-lite m))
  [:div.controls]   (add-spans errors help-inline help-block))

;;Creates a text-area form element
;;ex: [:name "namehere" :label "labelhere" :type "text-area"
;;     :size "xlarge" :rows "3" :default "defaultstuff"]
(defsnippet text-area-lite
  form-template
  [:div.text-area :textarea]
  [{:keys [name label size rows errors default class style custom-attrs id]
    :or {size "input-large"
         rows "3"
         default ""}}]
  [:textarea] (do-> (set-attr :class size :style style :name name :rows rows :id id)
                    (if custom-attrs
                      (apply set-attr custom-attrs)
                      identity)
                    (add-class class)
                    (content default)))

(defsnippet text-area-field
  form-template
  [:div.text-area]
  [{:keys [name label size rows errors default value help-inline help-block] :as m}]
  [:div.text-area] (do->
                    (handle-error-css errors))
  [:label] (do-> (set-attr :for name)
                 (content label))
  [:textarea] (constantly (text-area-lite m))
  [:div.controls] (add-spans errors help-inline help-block))

;;Creates a select (dropdown) form element with the given values
;;Ex: {:type "select" :name "cars" :size "xlarge" :label "Cars"
;;     :inputs [["volvo" "Volvo"] ["honda" "Honda"]]}
(defsnippet select-lite
  form-template
  [:div.select-dropdown :select]
  [{:keys [name size style class label inputs custom-inputs errors default type custom-attrs id]
    :or {size "input-large"}}]
  [:select] (do->
             (set-attr :name name :id (or id name) :style style :class size)
             (if custom-attrs
               (apply set-attr custom-attrs)
               identity)
             (add-class class)
             (if (string-contains? type "multiple")
               (set-attr :multiple "multiple")
               identity))
  ;; custom inputs attrs map ex: {:value "" :class "" :id ""}
  [:option] (if custom-inputs
              (clone-for [[label {:keys [value] :as attrs}]
                          custom-inputs]
                         (do-> #(assoc % :attrs attrs)
                               (if (= default value)
                                 (set-attr :selected "selected")
                                 identity)
                               (content label)))
              (clone-for [[value value-label] inputs]
                         (do-> (set-attr :value value)
                               (if (= default value)
                                 (set-attr :selected "selected")
                                 identity)
                               (content value-label)))))

(defsnippet select-field
  form-template
  [:div.select-dropdown]
  [{:keys [name size label inputs errors default type help-inline help-block hidden]
    :as m}]
  [:div.select-dropdown] (do->
                          (handle-error-css errors)
                          (if hidden
                            (add-class "hidden")
                            identity))
  [:label] (do-> (content label)
                 (set-attr :for name))
  [:select] (constantly (select-lite m))
  [:div.controls]  (add-spans errors help-inline help-block))

;;Creates a radio or checkbox form list with the given attributes
;; ex: {:type "select" :name "cars" :size "xlarge" :label "Cars"
;;      :inputs [["volvo" "Volvo"] ["honda" "Honda"]]}
;;custom-inputs format: [["OptionLabelOne" {:class "first" :value
;; "one"}]]
;;TO DO: CLEAN THIS UP
(defsnippet checkbox-or-radio-lite
  form-template
  [:div.checkbox-or-radio :div.controls :label]
  [{:keys [name inputs custom-inputs type errors default style custom-attrs]}]
  [:label] (if custom-inputs
             (clone-for [[value-label {:keys [value] :as attrs}] custom-inputs]
                        [:label] (do-> (set-attr :class type)
                                       (if (string-contains? type "inline")
                                         (add-class "inline")
                                         identity))
                        [:input] (do->
                                  #(assoc % :attrs attrs)
                                  (set-attr :type (first-word type)
                                            :name name
                                            :style style
                                            :id (remove-spaces
                                                 value))
                                  (if custom-attrs
                                    (apply set-attr custom-attrs)
                                    identity)
                                  (content value-label)
                                  (if (contains?
                                       (set (collectify default))
                                       value)
                                    (set-attr :checked "checked")
                                    identity)))
             (clone-for [[value value-label] inputs]
                        [:label] (do-> (set-attr :class type)
                                       (if (string-contains? type "inline")
                                         (add-class "inline")
                                         identity))
                        [:input] (do-> (set-attr :type (first-word type)
                                                 :name name
                                                 :style style
                                                 :value value
                                                 :id (remove-spaces
                                                      value))
                                       (content value-label)
                                       (if (contains?
                                            (set (collectify default))
                                            value)
                                         (set-attr :checked "checked")
                                         identity)))))

(defsnippet checkbox-or-radio
  form-template
  [:.checkbox-or-radio]
  [{:keys [class name label inputs default errors help-inline help-block hidden] :as m}]
  [:div.checkbox-or-radio]  (do->
                             (add-class class) ;;'checkbox' or 'radio'
                             (if hidden
                               (add-class "hidden")
                               identity)
                             (handle-error-css errors))
  [:label.control-label] (do-> (content label)
                               (set-attr :name name))
  [:div.controls] (content
                   (checkbox-or-radio-lite m))
  [:div.controls] (add-spans errors help-inline help-block))

;;Creates a file input button
(defsnippet file-input-lite
  form-template
  [:div.file-input :input]
  [{:keys [name label errors style custom-attrs]}]
  [:input] (do-> (set-attr :name name :style style)
                 (if custom-attrs
                   (apply set-attr custom-attrs)
                   identity)))

(defsnippet file-input
  form-template
  [:div.file-input]
  [{:keys [name label errors help-inline help-block] :as m}]
  [:div.file-input] (handle-error-css errors)
  [:label] (content label)
  [:input] (constantly (file-input-lite m))
  [:div.controls] (add-spans errors help-inline help-block))

;;Creates a submit button with a specified label (value)
;;TODO: add support for different types of buttons within <div class="actions">
(defsnippet button-lite
  form-template
  [:div#submit-button :button]
  [label class button-attrs]
  [:button] (do-> (content label)
                  (if (seq button-attrs)
                    (apply set-attr (flatten (into [] button-attrs)))
                    identity)
                  (add-class class)))

(defsnippet make-submit-button
  form-template
  [:div#submit-button]
  [label cancel-link button-attrs]
  [:button] (constantly (button-lite label "btn-primary" button-attrs))
  [:a] (if cancel-link
         (if (= cancel-link "modal")
           (set-attr :data-dismiss "modal")
           (set-attr :href cancel-link))
         (content "")))

;;HELPERS
(defn make-field-helper
  "Used by make-field, calls the right defsnippet (either the lite
  version in a single 'controls' div, or the full version wrapped in a
  'control-group')"
  [form-class field field-lite m]
  (if (string-contains? form-class "form-inline")
    (list (field-lite m) " ")
    (field m)))

(defsnippet inline-fields
  form-template
  [:div.inline-fields]
  [{:keys [name label type columns inline-content hidden help-inline]}]
  [:label] (content label)
  [:div.inline-fields] (do->
                        (handle-error-css (some (fn[a] a) (map :errors columns)))
                        (if hidden
                          (add-class "hidden")
                          identity))
  [:div.controls-row] (content
                       (interpose " " inline-content)
                       (if-let [err-msg (some (fn[a] a) (map :errors columns))]
                         (span "help-inline" err-msg)
                         (when help-inline
                           (span "help-inline" help-inline)))))

(defn make-field
  "Takes a single map representing a form element's attributes and
  routes it to the correct snippet based on its type. Supports input
  fields, text areas, dropdown menus, checkboxes, radios, and file
  inputs. Ex: {:type 'text' :name 'username' :label 'Username' :errors
  ['Incorrect username'] :default ''}"
  [form-class m]
  (case (first-word (:type m))
    "text"       (make-field-helper form-class
                                    input-field input-lite m)
    "hidden" (hidden-input m)
    "password"   (input-field (dissoc m :default)) ;;dont keep on
    ;;render
    "button" (make-field-helper form-class
                                input-field input-lite m)
    "text-area"  (make-field-helper form-class
                                    text-area-field text-area-lite m)
    "select"     (make-field-helper form-class
                                    select-field select-lite m)
    "radio"      (make-field-helper form-class
                                    checkbox-or-radio checkbox-or-radio-lite m)
    "checkbox"   (make-field-helper form-class
                                    checkbox-or-radio checkbox-or-radio-lite m)
    "inline-fields" (inline-fields (assoc m :inline-content
                                          (map
                                           #(make-field "form-inline" %)
                                           (:columns m))) )
    ;;(inline-fields m)
    "custom" (:html-nodes m)
    "file-input" (file-input m)))

(defn inline-errs-defs
  "Used by make-form to add errors and defaults for form fields of
  type 'inline-fields.' Adds an :errors and :default to each inline
  field."
  [{:keys [columns] :as m} errors-and-defaults]
  (let [new-columns (map #(merge %
                                 (get errors-and-defaults
                                      (keyword (:name %))))
                         columns)]
    (assoc m :columns new-columns)))

;;  ex: ({:type "text" :name "username" :label "Username"}
;;       {:type "pass" :name "password" :label "Password"})
;; after we merge with errors / defs, one field could look like:
;; {:type "text" :name "username" :errors ["username cannot be blank"] :default ""}
;;Submit-Label is the label on the submit button - default is "Submit"
(defn make-form
  "Returns a form with the specified action, fields, and submit
  button. Fields is a sequence of maps, each containing a form element's
  attributes."
  [& {:keys [action class fields submit-label errors-and-defaults enctype
             cancel-link legend button-type button-attrs method forms-attrs] :as form-map
      :or {class "form-horizontal"
           method "post"}}]
  (basic-form
   (-> (select-keys form-map [:action :legend :method :form-attrs :class :enctype])
       (assoc :fields (map (fn [{:keys [name type] :as a-field}]
                             (make-field class
                                         (merge a-field
                                                (if (string-contains? type "inline-fields")
                                                  (inline-errs-defs a-field errors-and-defaults)
                                                  (if (not (= type "custom"))
                                                    (get errors-and-defaults
                                                         (keyword
                                                          ;;replace [] in case its
                                                          ;;the name of a form
                                                          ;;element that can take
                                                          ;;on mutliple values (ie checkbox)
                                                          (string/replace name "[]" "")))
                                                    {})))))
                           fields)
              :submitter (if (string-contains? class "form-inline")
                           (button-lite submit-label button-type button-attrs)
                           (when submit-label
                             (make-submit-button submit-label cancel-link button-attrs)))))))


;;MACROS

;; @vali/*errors* looks like:
;;  {:title [["title must be an integer number!"]], :location
;;  [["location cannot be blank!"]]}
;; 'errors' are the sandbar :_validation-errors from the result of validating
;;  the form map.
;; WE DONT DO THIS ANYMORE
(defn move-errors-to-noir
  "Moves errors from sandbar to Noir and returns the form-data."
  [form-data errors]
  (comment (println "(FBS) Post request failed validation! Sandbar errors (moving to noir): "
                    errors "\n"))
  (doseq [[field [error]] errors]
    (vali/set-error field error))
  (assoc form-data :_sandbar-errors errors))

(defn move-errors-to-flash
  "Moves the errors from sandbar validation and any form-data (values
  that were just submitted) over to the flash to be used when we
  redirect to the form page again. Returns the form data with the sandbar errors."
  [form-data errors]
  (let [form-with-errors (assoc form-data
                           :_sandbar-errors errors)]
    (do
      (session/flash-put! :form-data form-with-errors)
      form-with-errors)))

(defn maybe-conj
  "Gets a list containing one map or many maps. If theres only one map
  in it, return the map. Else, return all the maps in the list conj-ed
  together."
  [a]
  (if (> (count a) 1)
    (apply conj a)
    (first a)))

(defn contains-keys?
  "Like contains? but you pass in a collection of as many keys as you
  want, only returns true if m contains ALL the given keys."
  [m ks]
  (if (next ks)
    (and (contains? m (first ks))
         (contains-keys? m (rest ks)))
    (contains? m (first ks))))

(defn file-input?
  "Checks to see if a given form param is for a file input."
  [m]
  (contains-keys? m [:size :content-type :tempfile :filename]))

;;The fn below is used called by the form-helper macro when a 'form
;;function' is called, ie in a defpage, ex: (myform m "action" "/").
;;It takes in the map of defaults, and checks to see if there is any
;;form-data in the flash (which signifies a failed validation
;;attempt). If there is no flash data, that means its loading the form
;;for the first time, and thus just uses the given defaults. If there
;;is form-data in the flash, then it uses the form params from there
;;and the errors that have been placed in there (by
;;move-errors-to-flash) and generates a map of default
;;and errors suitable for use by make-form.

(defn create-errors-defaults-map
  "Used when a 'form fn' is called, either during the first time a
  form is loaded or on a reload due to a validation error.  It takes
  in a map of default values (which could be empty) and returns a map
  with the form elements as keys, each paired with a map containing
  defaults and errors from validation. Ex: {:somekey {:errors ['error
  mesage'] :default 'default message here'}"
  [default-values] ;;values from a db or something
  ;;  (println "(FBS) Making form. All Noir errors: " @vali/*errors*)
  ;;  (println "(FBS) Making form. Form Params: " m)
  (let [flash-data (session/flash-get :form-data) ;;data from prev submission
        flash-errors (:_sandbar-errors flash-data) ;;errors
        m (if (seq flash-data)
            (dissoc flash-data :_sandbar-errors)
            default-values)
        ;;on first load uses default data (ie from db), then POST DATA ONLY on a reload
        defaults (if (seq m)
                   (maybe-conj
                    (map (fn[[k v]] {k {:errors nil :default (if (coll? v)
                                                              (if (file-input? v)
                                                                nil
                                                                (map str v))
                                                              (str v))}}) m))
                   {})
        errors (if (seq flash-errors)
                 (maybe-conj
                  (map (fn[[k v]] {k {:errors v :default ""}}) flash-errors))
                 {})
        errs-defs  (merge-with
                    (fn[a b] {:errors (:errors b) :default (:default a)})
                    defaults errors)]
    ;;    (println "(FBS) Noir ERRORS: " @vali/*errors*)
    ;;    (println "(FBS) Sandbar ERRORS: " (:_sandbar-errors form-map))
    ;; (println "(FBS) Making form. Computed errors / defaults map: " errs-defs)
    errs-defs))

;;Takes a validator function, an url (route) to POST to, a sequence of
;;maps each containing a form element's attributes, a submit label for
;;the form, and functions to call in the POST handler on success or
;;failure
(defmacro form-helper
  "Generates a function that can be used the make the form, and registers a POST handler with Noir. "
  [sym & {:keys [fields method post-url validator on-success on-failure submit-label]
          :or {on-success (constantly (response/redirect "/"))
               validator  identity
               method "post"}
          :as opts}]
  (assert (and post-url fields on-success on-failure)
          "Please provide :post-url, :fields, and :on-failure to form-helper.")
  `(do
     (defn ~sym
       ([defaults# action# cancel-link#]
          (->> (create-errors-defaults-map defaults#)
               ;;defaults are values that can be passed in from
               ;;something like a db. If you submitted a form with
               ;;data, and it failed validation, everything has
               ;;already been placed in the flash in
               ;;move-errors-to-flash. Create-errors-defaults-map can
               ;;access values from the flash and make a field that is
               ;;suitable to be passed to make-form
               (assoc (-> (assoc ~opts :action action#)
                          (assoc :cancel-link cancel-link#))
                 :errors-and-defaults)
               (apply concat)
               (apply make-form))))
     (defpage [:post ~post-url] {:as m#}
       (if-valid ~validator m#
                 ~on-success
                 (comp ~on-failure move-errors-to-flash)))))

;;Can Use post-helper with make-form when you don't have enough info to use
;;form-helper.
(defmacro post-helper
  [& {:keys [post-url validator on-success on-failure]}]
  `(defpage [:post ~post-url] {:as m#}
     ;;     (println "post-helper map: " m#)
     (if-valid ~validator m#
               ~on-success
               (comp ~on-failure move-errors-to-flash))))
