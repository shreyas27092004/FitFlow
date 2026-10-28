package com.fitflow.notification;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.Collections;

@Configuration
public class MailTemplateConfig {

    @Bean
    public TemplateEngine mailTemplateEngine() {
        SpringTemplateEngine engine = new SpringTemplateEngine();

        ClassLoaderTemplateResolver htmlResolver = new ClassLoaderTemplateResolver();
        htmlResolver.setPrefix("mail/");
        htmlResolver.setTemplateMode(TemplateMode.HTML);
        htmlResolver.setCharacterEncoding("UTF-8");
        htmlResolver.setResolvablePatterns(Collections.singleton("*.html"));
        htmlResolver.setOrder(1);

        ClassLoaderTemplateResolver textResolver = new ClassLoaderTemplateResolver();
        textResolver.setPrefix("mail/");
        textResolver.setTemplateMode(TemplateMode.TEXT);
        textResolver.setCharacterEncoding("UTF-8");
        textResolver.setResolvablePatterns(Collections.singleton("*.txt"));
        textResolver.setOrder(2);

        engine.addTemplateResolver(htmlResolver);
        engine.addTemplateResolver(textResolver);
        return engine;
    }
}
