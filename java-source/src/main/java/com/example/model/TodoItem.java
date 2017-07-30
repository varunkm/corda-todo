package com.example.model;

import net.corda.core.serialization.CordaSerializable;

/**
 * Created by varunmathur on 01/07/2017.
 */
@CordaSerializable
public class TodoItem {
    private String title;
    private String description;
    private boolean complete;

    public TodoItem(String title, String description, boolean complete)
    {
        this.title = title;
        this.description = description;
        this.complete = complete;
    }

    public String getTitle()
    {
        return title;
    }

    public String getDescription()
    {
        return description;
    }

    public boolean isComplete()
    {
        return complete;
    }
}
