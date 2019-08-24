package unimelb.bitbox;

import unimelb.bitbox.util.Document;

/**
 * Usage:
 * Use option table to manage the commands.
 */
@FunctionalInterface
public interface OperationMonitor {

    void run(Document request) throws Exception;

}