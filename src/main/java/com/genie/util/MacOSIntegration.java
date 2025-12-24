package com.genie.util;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Native macOS integration for menu bar app behavior.
 * Uses JNA to call Objective-C runtime and set NSApplication activation policy.
 */
public class MacOSIntegration {
    
    private static final Logger logger = LoggerFactory.getLogger(MacOSIntegration.class);
    
    // Objective-C runtime interface
    public interface ObjCRuntime extends Library {
        ObjCRuntime INSTANCE = Native.load("objc", ObjCRuntime.class);
        
        Pointer objc_getClass(String className);
        Pointer sel_registerName(String selectorName);
        Pointer objc_msgSend(Pointer receiver, Pointer selector, Object... args);
    }
    
    // NSApplicationActivationPolicy values
    private static final long NSApplicationActivationPolicyRegular = 0;
    private static final long NSApplicationActivationPolicyAccessory = 1;
    private static final long NSApplicationActivationPolicyProhibited = 2;
    
    /**
     * Hide the app from the Dock by setting activation policy to "accessory".
     * This makes the app a pure menu bar app (like Susu).
     */
    public static void hideFromDock() {
        if (!isMacOS()) {
            logger.debug("Not macOS, skipping dock hiding");
            return;
        }
        
        try {
            ObjCRuntime objc = ObjCRuntime.INSTANCE;
            
            // Get NSApplication class
            Pointer nsAppClass = objc.objc_getClass("NSApplication");
            if (nsAppClass == null) {
                logger.warn("Could not get NSApplication class");
                return;
            }
            
            // Get sharedApplication selector
            Pointer sharedAppSel = objc.sel_registerName("sharedApplication");
            
            // Get NSApp (shared application instance)
            Pointer nsApp = objc.objc_msgSend(nsAppClass, sharedAppSel);
            if (nsApp == null) {
                logger.warn("Could not get shared NSApplication instance");
                return;
            }
            
            // Get setActivationPolicy: selector
            Pointer setActivationPolicySel = objc.sel_registerName("setActivationPolicy:");
            
            // Set activation policy to Accessory (hides from dock)
            objc.objc_msgSend(nsApp, setActivationPolicySel, NSApplicationActivationPolicyAccessory);
            
            logger.info("Successfully set NSApplication activation policy to Accessory (hidden from Dock)");
            
        } catch (Exception e) {
            logger.error("Failed to hide from dock", e);
        }
    }
    
    /**
     * Show the app in the Dock by setting activation policy to "regular".
     * Useful if you need to temporarily show dock icon.
     */
    public static void showInDock() {
        if (!isMacOS()) {
            return;
        }
        
        try {
            ObjCRuntime objc = ObjCRuntime.INSTANCE;
            
            Pointer nsAppClass = objc.objc_getClass("NSApplication");
            Pointer sharedAppSel = objc.sel_registerName("sharedApplication");
            Pointer nsApp = objc.objc_msgSend(nsAppClass, sharedAppSel);
            Pointer setActivationPolicySel = objc.sel_registerName("setActivationPolicy:");
            
            objc.objc_msgSend(nsApp, setActivationPolicySel, NSApplicationActivationPolicyRegular);
            
            logger.info("Set NSApplication activation policy to Regular (visible in Dock)");
            
        } catch (Exception e) {
            logger.error("Failed to show in dock", e);
        }
    }
    
    private static boolean isMacOS() {
        String os = System.getProperty("os.name").toLowerCase();
        return os.contains("mac");
    }
}

