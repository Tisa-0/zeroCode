package io.dataease.utils;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
public class CommonBeanFactory implements ApplicationContextAware {
    private static ApplicationContext context;

    public CommonBeanFactory() {
    }

    public void setApplicationContext(ApplicationContext ctx) throws BeansException {
        context = ctx;
    }

    public static Object getBean(String beanName) {
        try {
            return context != null && StringUtils.isNotBlank(beanName) ? context.getBean(beanName) : null;
        } catch (BeansException e) {
            return null;
        }
    }

    public static <T> T getBean(Class<T> className) {
        try {
            return context != null && className != null ? context.getBean(className) : null;
        } catch (BeansException e) {
            return null;
        }
    }
    public static ApplicationContext getApplicationContext() {
        return context;
    }

    public static <T> T proxy(Class<T> className) {
        try {
            return context != null && className != null ? context.getBean(className) : null;
        } catch (BeansException e) {
            return null;
        }
    }
}
