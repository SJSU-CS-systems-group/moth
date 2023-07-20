package edu.sjsu.moth.util;

import org.springframework.beans.factory.FactoryBean;

import static org.mockito.Mockito.mock;

/**
 * we need this factory because we want to mack a class that has autowired fields. this factory will instantiate
 * a mock of the bean without trying to resolve the autowires of the underlying class.
 * from https://stackoverflow.com/questions/26016311/how-to-disable-spring-autowiring-in-unit-tests-for-configuration
 * -bean-usage
 */
public class MockitoFactoryBean<T> implements FactoryBean<T> {
    private final Class<T> clazz;
    private final T obj;

    public MockitoFactoryBean(Class<T> clazz) {
        this.clazz = clazz;
        this.obj = mock(clazz);
    }

    @Override
    public T getObject() throws Exception {
        return obj;
    }

    @Override
    public Class<T> getObjectType() {
        return clazz;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }
}