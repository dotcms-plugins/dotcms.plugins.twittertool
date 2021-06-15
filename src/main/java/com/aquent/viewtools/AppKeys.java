package com.aquent.viewtools;

public enum AppKeys {

    DEBUG("twitter4jDebug"),
    CONSUMER_KEY("twitter4jConsumerKey"),
    CONSUMER_SECRET("twitter4jConsumerSecret"),
    ACCESS_TOKEN("twitter4jAccessToken"),
    TOKEN_SECRET("twitter4jTokenSecret");


    final public String key;

    AppKeys(String key){
        this.key=key;
    }


    public final static String APP_KEY = "dotTwitterApp";

    public final static String APP_YAML_NAME = APP_KEY + ".yml";
}
