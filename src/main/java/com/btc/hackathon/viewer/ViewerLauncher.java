package com.btc.hackathon.viewer;

/**
 * Einstiegspunkt der Anwendung.
 *
 * <p>Diese Klasse erbt bewusst <em>nicht</em> von {@code Application}. Startet man ein
 * Fat-JAR, liegt JavaFX im Classpath statt im Modulepath; der JDK-Starter prueft dann, ob
 * die Hauptklasse von {@code Application} abgeleitet ist, und bricht mit
 * "JavaFX runtime components are missing" ab. Ein schlichter Zwischenstarter umgeht das.
 */
public final class ViewerLauncher {

    private ViewerLauncher() {
    }

    public static void main(String[] args) {
        ViewerApp.run(args);
    }
}
