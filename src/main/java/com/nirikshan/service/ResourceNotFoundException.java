package com.nirikshan.service;
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String type, Long id) { super(type + " not found: " + id); }
}
