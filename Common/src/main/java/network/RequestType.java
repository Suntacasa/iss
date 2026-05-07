package network;

public enum RequestType {
    LOGIN,
    LOGOUT,
    CREATE_TASK,
    EDIT_TASK,
    DELETE_TASK,
    GET_ALL_TASKS,
    GET_AVAILABLE_TASKS,
    GET_AVAILABLE_TASKS_BY_PRIORITY, // UC-9
    GET_WORKER_DASHBOARD,
    SELF_ASSIGN_TASK,
    ASSIGN_TASK_TO_WORKER,           // UC-5
    MARK_TASK_COMPLETE,              // UC-7
    DROP_TASK,                       // UC-11
    GET_ALL_WORKERS,                 // UC-5 (fetch worker list for dialog)
    GET_WORKER_LOGS                  // UC-10
}