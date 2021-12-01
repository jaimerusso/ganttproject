/*
 * Created on 25.04.2005
 */
package net.sourceforge.ganttproject.application;

import biz.ganttproject.LoggerApi;
import biz.ganttproject.app.InternationalizationKt;
import kotlin.Unit;
import net.sourceforge.ganttproject.AppBuilder;
import net.sourceforge.ganttproject.GPLogger;
import net.sourceforge.ganttproject.GPVersion;
import net.sourceforge.ganttproject.GanttProject;
import net.sourceforge.ganttproject.document.DocumentCreator;
import net.sourceforge.ganttproject.export.CommandLineExportApplication;
import org.eclipse.core.runtime.IPlatformRunnable;

import java.awt.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * @author bard
 */
public class MainApplication implements IPlatformRunnable {
  private AtomicBoolean myLock = new AtomicBoolean(true);
  private final LoggerApi logger = GPLogger.create("Window");

  // The hack with waiting is necessary because when you
  // launch Runtime Workbench in Eclipse, it exists as soon as
  // GanttProject.main() method exits
  // without Eclipse, Swing thread continues execution. So we wait until main
  // window closes
  @Override
  public Object run(Object args) throws Exception {
    Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
    String[] cmdLine = (String[]) args;

    var appBuilder = new AppBuilder(cmdLine);
    if (appBuilder.getMainArgs().help) {
      appBuilder.getCliParser().usage();
      System.exit(0);
    }
    if (appBuilder.getMainArgs().version) {
      System.out.println(GPVersion.getCurrentVersionNumber());
      System.exit(0);
    }

    appBuilder.withLogging();
    if (!appBuilder.isCli()) {
      appBuilder.withSplash();
      appBuilder.withWindowVisible();
      appBuilder.whenWindowOpened(frame -> {
        DocumentCreator.createAutosaveCleanup().run();
        return Unit.INSTANCE;
      });
      if (appBuilder.getMainArgs().fixMenuBarTitle) {
        appBuilder.whenWindowOpened(frame -> {
          try {
            var toolkit = Toolkit.getDefaultToolkit();
            var awtAppClassNameField = toolkit.getClass().getDeclaredField("awtAppClassName");
            awtAppClassNameField.setAccessible(true);
            awtAppClassNameField.set(toolkit, InternationalizationKt.getRootLocalizer().formatText("appliTitle"));
          } catch (NoSuchFieldException ex) {
            System.err.println("Can't set awtAppClassName (needed on Linux to show app name in the top panel)");
          } catch (IllegalAccessException ex) {
            System.err.println("Can't set awtAppClassName (needed on Linux to show app name in the top panel)");
          }
          return Unit.INSTANCE;
        });
      }
    } else {
      appBuilder.whenDocumentReady(project -> {
        var cliApp = new CommandLineExportApplication();
        cliApp.export(appBuilder.getCliArgs(), project, ((GanttProject)project).getUIFacade());
        return Unit.INSTANCE;
      });
    }
    var files = appBuilder.getMainArgs().file;
    if (files != null && !files.isEmpty()) {
      appBuilder.withDocument(files.get(0));
    }


    Consumer<Boolean> onApplicationQuit = withSystemExit -> {
      synchronized(myLock) {
        myLock.set(withSystemExit);
        myLock.notify();
      }
    };
    GanttProject.setApplicationQuitCallback(onApplicationQuit);
    appBuilder.launch();
    synchronized (myLock) {
      logger.debug("Waiting until main window closes");
      myLock.wait();
      logger.debug("Main window has closed");
    }
    logger.debug("Program terminated");
    GPLogger.close();
    if (myLock.get()) {
      System.exit(0);
    }
    return null;
  }

}
