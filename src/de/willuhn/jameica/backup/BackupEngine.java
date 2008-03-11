/**********************************************************************
 * $Source: /cvsroot/jameica/jameica/src/de/willuhn/jameica/backup/BackupEngine.java,v $
 * $Revision: 1.6 $
 * $Date: 2008/03/11 00:13:08 $
 * $Author: willuhn $
 * $Locker:  $
 * $State: Exp $
 *
 * Copyright (c) by willuhn software & services
 * All rights reserved
 *
 **********************************************************************/

package de.willuhn.jameica.backup;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.zip.ZipFile;

import de.willuhn.io.FileFinder;
import de.willuhn.io.ZipCreator;
import de.willuhn.io.ZipExtractor;
import de.willuhn.jameica.system.Application;
import de.willuhn.logging.Logger;
import de.willuhn.util.ApplicationException;
import de.willuhn.util.ProgressMonitor;


/**
 * Klasse mit statischen Funktionen, die das Backup ubernehmen.
 */
public class BackupEngine
{
  private final static DateFormat format = new SimpleDateFormat("yyyyMMdd__HH_mm_ss");
  private final static String PREFIX = "jameica-backup-";
  private final static String MARKER = ".restore";
  
  /**
   * Liefert eine Liste der bisher erstellten Backups.
   * @param dir das Verzeichnis, in dem nach Backups gesucht werden soll.
   * Ist es nicht angegeben, wird das aktuelle Default-Verzeichnis verwendet.
   * @return eine Liste der Backups in diesem Verzeichnis.
   * @throws ApplicationException
   */
  public static synchronized BackupFile[] getBackups(String dir) throws ApplicationException
  {
    String s = dir == null ? Application.getConfig().getBackupDir() : dir;
    FileFinder finder = new FileFinder(new File(s));
    finder.extension("zip");
    finder.matches("^" + PREFIX + ".*");
    File[] found = finder.find();
    if (found == null)
      return new BackupFile[0];

    // Nach Name sortieren
    Arrays.sort(found);
    ArrayList backups = new ArrayList();
    for (int i=0;i<found.length;++i)
    {
      try
      {
        backups.add(new BackupFile(found[i]));
      }
      catch (ApplicationException e)
      {
        Logger.error("skipping invalid backup: " + found[i].getAbsolutePath() + ": " + e.getMessage());
      }
    }
    return (BackupFile[])backups.toArray(new BackupFile[backups.size()]);
  }
  
  /**
   * Macht eine ggf. vorhandene Auswahl der Backup-Wiederherstellung rueckgaengig.
   */
  public static synchronized void undoRestoreMark()
  {
    File marker = new File(Application.getConfig().getWorkDir(),MARKER);
    if (marker.exists())
      marker.delete();
  }

  /**
   * Markiert das uebergebene Backup fuer die Wiederherstellung.
   * Das eigentliche Wiederherstellen der Daten geschieht beim
   * naechsten Neustart der Anwendung.
   * @param backup das zurueckzusichernde Backup.
   * @throws ApplicationException
   */
  public static synchronized void markForRestore(BackupFile backup) throws ApplicationException
  {
    if (backup == null)
      throw new ApplicationException(Application.getI18n().tr("Bitte w�hlen Sie das wiederherzustellende Backup aus"));
    
    File file = backup.getFile();
    if (!file.isFile() || !file.canRead())
      throw new ApplicationException(Application.getI18n().tr("Datei nicht lesbar. Stellen Sie bitte sicher, dass Sie Schreibrechte f�r sie besitzen."));
    
    Logger.warn("activating backup for restore: " + file.getAbsolutePath());
    File marker = new File(Application.getConfig().getWorkDir(),MARKER);
    Writer writer = null;
    try
    {
      writer = new BufferedWriter(new FileWriter(marker));
      writer.write(file.getAbsolutePath());
      writer.flush();
    }
    catch (Exception e)
    {
      Logger.error("unable to store marker file",e);
      throw new ApplicationException(Application.getI18n().tr("Fehler beim Aktivieren der Backup-Datei. Pr�fen Sie bitte das System-Log"));
    }
    finally
    {
      if (writer != null)
      {
        try
        {
          writer.close();
        }
        catch (Exception e)
        {
          Logger.error("unable to close marker file",e);
          throw new ApplicationException(Application.getI18n().tr("Fehler beim Aktivieren der Backup-Datei. Pr�fen Sie bitte das System-Log"));
        }
      }
    }
  }

  /**
   * Liefert das ggf aktuell zur Wiederherstellung vorgemerkte Backup.
   * @return das aktuell vorgemerkte Backup oder null
   * @throws ApplicationException
   */
  public static BackupFile getCurrentRestore() throws ApplicationException
  {
    File marker = new File(Application.getConfig().getWorkDir(),MARKER);
    if (!marker.exists() || !marker.canRead())
      return null;

    BufferedReader reader = null;
    try
    {
      reader = new BufferedReader(new FileReader(marker));
      File f = new File(reader.readLine());
      if (f.canRead() && f.isFile())
        return new BackupFile(f);
      return null;
    }
    catch (ApplicationException ae)
    {
      throw ae;
    }
    catch (Exception e)
    {
      Logger.error("unable to read marker file",e);
    }
    finally
    {
      if (reader != null)
      {
        try
        {
          reader.close();
        }
        catch (Exception e)
        {
          Logger.error("unable to close marker file",e);
        }
      }
    }
    return null;
  }
  
  /**
   * Fuehrt das Backup-Restore durch.
   * @param monitor
   * @throws ApplicationException
   */
  public static synchronized void doRestore(ProgressMonitor monitor) throws ApplicationException
  {
    monitor.setStatusText("check backup");
    BackupFile backup = getCurrentRestore();
    if (backup == null)
    {
      // Huh? Wie kann das sein?
      Logger.error("SUSPEKT: no backup to restore found");
      throw new ApplicationException(Application.getI18n().tr("Wiederherzustellendes Backup nicht gefunden"));
    }
    
    try
    {
      // Restore-Marker loeschen. Muessen wir vor der Erstellung des Backups machen
      BackupEngine.undoRestoreMark();

      // Wir machen nochmal ein frisches Backup
      // Aber ohne alte Backups zu rotieren, das wuerde ggf. das wiederherzustellende
      // Backup loeschen
      monitor.setStatusText("creating backup");
      File[] content = BackupEngine.doBackup(monitor, false);

      // Jetzt loeschen wir die gerade gesicherten Daten
      if (content == null || content.length == 0)
        throw new ApplicationException(Application.getI18n().tr("Aktuelles Backup enthielt keine Daten. Wiederherstellung abgebrochen"));

      // So, jetzt loeschen wir aber wirklich
      monitor.setStatusText("cleanup work dir");
      for (int i=0;i<content.length;++i)
      {
        Logger.info("purge " + content[i]);
        deleteRecursive(content[i]);
      }

      // Und sichern das Backup zurueck
      monitor.setStatusText("restoring backup " + backup.getFile().getAbsolutePath());
      File workdir = new File(Application.getConfig().getWorkDir());
      ZipExtractor ext = new ZipExtractor(new ZipFile(backup.getFile()));
      ext.setMonitor(monitor);
      ext.extract(workdir);
      monitor.setStatusText("restore completed");
    }
    catch (ApplicationException ae)
    {
      throw ae;
    }
    catch (Exception e)
    {
      Logger.error("unable to restore backup",e);
      throw new ApplicationException(Application.getI18n().tr("Fehler beim Wiederherstellen des Backups: " + e.getMessage()));
    }
  }
  
  /**
   * Erstellt ein frisches Backup.
   * @return Liste der gesicherten Verzeichnisse
   * @param true, wenn alte Backups rotiert werden sollen.
   * @throws ApplicationException
   */
  public static synchronized File[] doBackup(ProgressMonitor monitor, boolean rotate) throws ApplicationException
  {
    // Sollen ueberhaupt Backups erstellt werden?
    if (!Application.getConfig().getUseBackup())
      return null;
    
    // Backup erzeugen
    ZipCreator zip  = null;
    Exception error = null;
    
    try
    {
      if (getCurrentRestore() != null)
      {
        monitor.setStatusText("restore marker found, skipping current backup");
        return null;
      }

      File workdir    = new File(Application.getConfig().getWorkDir());
      String filename = PREFIX + format.format(new Date()) + ".zip";
      File dir        = new File(Application.getConfig().getBackupDir());
      File backup     = new File(dir,filename);

      if (backup.exists())
        throw new ApplicationException(Application.getI18n().tr("Backup-Datei {0} existiert bereits",backup.getAbsolutePath()));

      ArrayList content = new ArrayList();
      monitor.setStatusText("creating backup " + backup.getAbsolutePath());
      zip = new ZipCreator(new BufferedOutputStream(new FileOutputStream(backup)));
      zip.setMonitor(monitor);
      File[] children = workdir.listFiles();
      for (int i=0;i<children.length;++i)
      {
        if (!children[i].isDirectory())
          continue; // Wir sichern nur Unterverzeichnisse. Also keine Backups (rekursiv) und Logs
        if (children[i].getCanonicalFile().equals(dir.getCanonicalFile()))
          continue; // Das Backup-Verzeichnis selbst ist ein Unterverzeichnis. Nicht sichern wegen Rekursion
        zip.add(children[i]);
        content.add(children[i]);
      }
      // Muessen wir vorher schliessen, weil das anschliessende getBackups()
      // sonst ein "java.util.zip.ZipException: error in opening zip file" wirft.
      zip.close();
      zip = null;

      if (rotate)
      {
        int maxCount = Application.getConfig().getBackupCount();
        BackupFile[] old = getBackups(Application.getConfig().getBackupDir());
        File[] toDelete = new File[old.length];
        for (int i=0;i<old.length;++i)
          toDelete[i] = old[i].getFile();

        // Sortieren
        Arrays.sort(toDelete);
        
        // Von oben mit dem Loeschen anfangen
        // Und solange loeschen wie:
        // Urspruengliche Anzahl - geloeschte > maximale Anzahl
        for (int pos=0;(toDelete.length - pos) > maxCount;++pos)
        {
          File current = toDelete[pos];
          monitor.setStatusText("delete old backup " + current.getAbsolutePath());
          current.delete();
          pos++;
        }
      }

      monitor.setStatusText("backup created");
      return (File[]) content.toArray(new File[content.size()]);
    }
    catch (ApplicationException ae)
    {
      error = ae;
      throw ae;
    }
    catch (Exception e)
    {
      error = e;
      Logger.error("unable to create backup",e);
      throw new ApplicationException(Application.getI18n().tr("Fehler beim Erstellen des Backups: " + e.getMessage()));
    }
    finally
    {
      if (zip != null)
      {
        try
        {
          zip.close();
        }
        catch (Exception e)
        {
          // Nur werfen, wenn es kein Folgefehler ist
          // Ansonsten interessiert es uns nicht mehr
          // weil die ZIP-Datei eh im Eimer ist
          if (error == null)
          {
            Logger.error("unable to close backup",e);
            throw new ApplicationException(Application.getI18n().tr("Fehler beim Erstellen des Backups: " + e.getMessage()));
          }
        }
      }
    }
  }
  
  /**
   * Loescht ein Verzeichnis rekursiv.
   * @param dir das rekursiv zu loeschende Verzeichnis.
   * @throws IOException wenn beim Loeschen ein Fehler auftrat.
   */
  private static boolean deleteRecursive(File dir) throws IOException
  {
    File candir = dir.getCanonicalFile();

    // Wen sich der kanonische Pfad vom absoluten unterscheidet,
    // ist es ein Symlink. Es waere viel zu gefaehrlich, dem zu folgen
    if (!candir.equals(dir.getAbsoluteFile()))
      return false;

    // Rekursion
    File[] files = candir.listFiles();
    if (files != null)
    {
      for (int i=0;i<files.length;++i)
      {
        // Wenn es sich so loeschen laesst, war es eine Datei,
        // ein leeres Verzeichnis oder ein Symlink. Im letzteren
        // Fall haben wir nur den Symlink geloescht, sind ihm
        // jedoch nicht gefolgt
        boolean deleted = files[i].delete();
        if (!deleted)
        {
          if (files[i].isDirectory())
            deleteRecursive(files[i]); // Scheint ein Verzeichnis zu sein?
        }
      }
    }

    // So, fertig. Verzeichnis selbst kann geloescht werden
    return dir.delete();  
  }
}


/**********************************************************************
 * $Log: BackupEngine.java,v $
 * Revision 1.6  2008/03/11 00:13:08  willuhn
 * @N Backup scharf geschaltet
 *
 * Revision 1.5  2008/03/07 17:30:15  willuhn
 * @N Splash-Screen-Ausgaben auch ins Log schreiben
 * @B Fehler im Dateformat des Backup (12- statt 24h-Uhr)
 *
 * Revision 1.4  2008/03/07 16:31:49  willuhn
 * @N Implementierung eines Shutdown-Splashscreens zur Anzeige des Backup-Fortschritts
 *
 * Revision 1.3  2008/03/07 01:36:27  willuhn
 * @N ZipCreator
 * @N Erster Code fuer Erstellung des Backups
 *
 * Revision 1.2  2008/03/03 09:43:54  willuhn
 * @N DateUtil-Patch von Heiner
 * @N Weiterer Code fuer das Backup-System
 *
 * Revision 1.1  2008/02/29 19:02:31  willuhn
 * @N Weiterer Code fuer Backup-System
 *
 * Revision 1.1  2008/02/29 01:12:30  willuhn
 * @N Erster Code fuer neues Backup-System
 * @N DirectoryInput
 * @B Fixes an FileInput, TextInput
 *
 **********************************************************************/
