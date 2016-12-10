/*
 * MediathekView
 * Copyright (C) 2008 W. Xaver
 * W.Xaver[at]googlemail.com
 * http://zdfmediathk.sourceforge.net/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package mSearch.filmlisten;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Iterator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import mSearch.daten.DatenFilm;
import mSearch.daten.ListeFilme;
import mSearch.Const;
import mSearch.tool.Log;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.XZOutputStream;
import java.io.FileWriter;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVFormat;
import java.util.ArrayList;
import java.util.List;


public class WriteFilmlistJson {

  private static final String NEW_LINE_SEPARATOR = "\n";

    public void filmlisteSchreibenJson(String datei, ListeFilme listeFilme) {




        ZipOutputStream zipOutputStream = null;
        XZOutputStream xZOutputStream = null;
        JsonGenerator jg = null;

        FileWriter fileWriter = null;
        CSVPrinter csvFilePrinter = null;
        try {
            Log.sysLog("Filme schreiben (" + listeFilme.size() + " Filme) :");
            File file = new File(datei);
            File dir = new File(file.getParent());
            if (!dir.exists()) {
                if (!dir.mkdirs()) {
                    Log.errorLog(915236478, "Kann den Pfad nicht anlegen: " + dir.toString());
                }
            }
            Log.sysLog("   --> Start Schreiben nach: " + datei);


            CSVFormat csvFileFormat = CSVFormat.DEFAULT.withDelimiter(';').withQuote('\'').withRecordSeparator("\n");
            fileWriter = new FileWriter(datei);
            csvFilePrinter = new CSVPrinter(fileWriter, csvFileFormat);

            // Infos der Felder in der Filmliste
            csvFilePrinter.printRecord(DatenFilm.COLUMN_NAMES);


            //Filme schreiben
            DatenFilm datenFilm;
            Iterator<DatenFilm> iterator = listeFilme.iterator();
            while (iterator.hasNext()) {
                datenFilm = iterator.next();
                datenFilm.arr[DatenFilm.FILM_NEU] = Boolean.toString(datenFilm.isNew()); // damit wirs beim nächsten Programmstart noch wissen

                List<String> filmRecord = new ArrayList<String>();
                for (int i = 0; i < DatenFilm.JSON_NAMES.length; ++i) {
                  int m = DatenFilm.JSON_NAMES[i];
                  filmRecord.add(datenFilm.arr[m].replace("\n","").replace("\r",""));
                }
                csvFilePrinter.printRecord(filmRecord);
            }
            Log.sysLog("   --> geschrieben!");
        } catch (Exception ex) {
            Log.errorLog(846930145, ex, "nach: " + datei);
        } finally {
            try {
              fileWriter.flush();
              fileWriter.close();
              csvFilePrinter.close();
            } catch (Exception e) {
                Log.errorLog(732101201, e, "close stream: " + datei);
            }
        }
    }

}
