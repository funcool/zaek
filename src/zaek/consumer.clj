(ns zaek.consumer
  (:require [clojure.walk :refer [stringify-keys keywordize-keys]])
  (:import com.rabbitmq.client.DefaultConsumer
           com.rabbitmq.client.Channel
           com.rabbitmq.client.Envelope
           com.rabbitmq.client.AMQP$BasicProperties))

(defprotocol IConsumer
  (onmessage [_ tag env properties body] "Consume a message."))

(defprotocol IConsumerCancel
  (oncancel [_ tag reason] "Callback for notify the cancelation of consumer."))

(extend-protocol IConsumer
  clojure.lang.AFn
  (onmessage [f tag env properties body]
    (f tag env properties body)))

(defn build-props
  "Build basic props instance from options hash-map."
  {:no-doc true :version "0.1"}
  [{:keys [mode content-type userid appid messageid reply-to type priority]
    :or {priority 0 mode 2 content-type "application/octet-stream"} :as options}]
  (let [builder (com.rabbitmq.client.AMQP$BasicProperties$Builder.)
        headers (dissoc options
                        :mode :content-type :userid :appid
                        :messageid :reply-to :type :priority)]
    (.contentType builder (name content-type))
    (.priority builder (.intValue priority))
    (.deliveryMode builder (.intValue mode))

    (when userid
      (.userId builder (name userid)))
    (when appid
      (.appId builder (name appid)))
    (when messageid
      (.messageId builder (name messageid)))
    (when reply-to
      (.replyTo builder (name reply-to)))
    (when type
       (.type builder (name type)))
    (when (seq headers)
      (.headers builder (-> (stringify-keys headers)
                            (java.util.HashMap.))))
    (.build builder)))

(defn props->map
  "Convert amqp basic props into a clojure hash-map."
  {:no-doc true :version "0.1"}
  [^AMQP$BasicProperties props]
  (let [basic {:userid (.getUserId props)
               :appid (.getAppId props)
               :content-type (.getContentType props)
               :messageid (.getMessageId props)
               :reply-to (.getReplyTo props)
               :type (.getType props)
               :mode (.getDeliveryMode props)
               :priority (.getPriority props)}]
    (merge basic (keywordize-keys (into {} (.getHeaders props))))))

(defn adapt
  "Adapt a consumer instance into an object that
  RabbitMQ java client understand."
  {:no-doc true :version "0.1"}
  [consumer channel]
  (let [^Channel chan (.-chan channel)]
    (proxy [DefaultConsumer] [chan]
      (handleDelivery [tag env properties body]
        (let [props (props->map properties)
              tag (.getDeliveryTag ^Envelope env)]
          (onmessage consumer tag env props body)))
      (handleCancel [tag]
        (when (satisfies? IConsumerCancel consumer)
          (oncancel consumer tag :external)))
      (handleCancelOk [tag]
        (when (satisfies? IConsumerCancel consumer)
          (oncancel consumer tag :user))))))


