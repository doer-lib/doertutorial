package com.doertutorial;

import com.doer.AcceptStatus;
import com.doer.Task;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class OrderProcessor {

    @AcceptStatus("New order created")
    public void startOrderProcessing(Task task) {
        task.setStatus(null);
    }
}
