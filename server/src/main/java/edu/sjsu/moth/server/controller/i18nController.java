package edu.sjsu.moth.server.controller;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.thymeleaf.spring5.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.Locale;
import java.util.ResourceBundle;

@Configuration
public class i18nController {
    //from baeldung https://www.baeldung.com/java-localize-exception-messages
    public static String getExceptionMessage(String key, Locale locale) {
        return ResourceBundle.getBundle("messages", locale).getString(key);
    }

    @Bean
    // message source is responsible for retrieving messages based on current locale. it loads different message sets
    // and
    // applies as necessary. message sets r based off of 'basename'. (e.g, spanish is messages_es now since basename is
    // messages
    public MessageSource messageSource() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        return messageSource;
    }

    // this method searches for the template to dynamically load based on prefix (filepath) and suffix. required for
    // template engine.
    public ClassLoaderTemplateResolver templateResolver() {
        ClassLoaderTemplateResolver templateSettings = new ClassLoaderTemplateResolver();
        templateSettings.setPrefix(
                "static/oauth/"); // classpath for the template, if/when we need more pages in the future we
        //should probably rename this folder for readability
        templateSettings.setSuffix(".html"); // suffix for templates so it knows what to load
        templateSettings.setTemplateMode("HTML"); // can switch between HTML, XML, and text
        templateSettings.setCharacterEncoding("UTF-8"); //only involves reading, doesn't overlap messageSource encoding
        return templateSettings;
    }

    @Bean
    // CORE component of Thymeleaf -- it takes and parses a template, requires context for various details e.g locale,
    // and renders page, outputting HTML
    // all current and future autowires will refer to this one
    public SpringTemplateEngine springTemplateEngine() {
        SpringTemplateEngine springTemplateEngine = new SpringTemplateEngine();
        springTemplateEngine.setMessageSource(messageSource()); // message sets, defined above
        springTemplateEngine.setTemplateResolver(templateResolver());
        return springTemplateEngine;
    }
}