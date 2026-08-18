package com.nirikshan.model;

/** Advisory gate action; Nirikshan never operates a physical gate. */
public enum GateActionType {
    KEEP_GATE_OPEN, OPEN_ALTERNATE_EXIT, CLOSE_ENTRY_GATE, TEMPORARILY_CLOSE_EXIT, NO_CHANGE
}
