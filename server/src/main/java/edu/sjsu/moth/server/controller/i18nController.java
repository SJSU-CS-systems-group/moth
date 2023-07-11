package edu.sjsu.moth.server.controller;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;

@Configuration
public class i18nController {
    @Bean
    // message source is responsible for retrieving messages based on current locale. it loads different message sets and
    // applies as necessary. message sets r based off of 'basename'. (e.g, spanish is messages_es now since basename is
    // messages
    public MessageSource messageSource() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        return messageSource;
    }
}