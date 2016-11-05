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
package mSearch.filmeSuchen.sender;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import mSearch.Config;
import mSearch.Const;
import mSearch.daten.DatenFilm;
import mSearch.filmeSuchen.FilmeSuchen;
import mSearch.filmeSuchen.GetUrl;
import mSearch.tool.Log;
import mSearch.tool.MSStringBuilder;

public class MediathekZdf extends MediathekReader implements Runnable {

    public final static String SENDERNAME = "ZDF";
    private final MSStringBuilder seite = new MSStringBuilder(Const.STRING_BUFFER_START_BUFFER);
    LinkedListUrl listeTage = new LinkedListUrl();

    public MediathekZdf(FilmeSuchen ssearch, int startPrio) {
        super(ssearch, SENDERNAME, 5 /* threads */, 150 /* urlWarten */, startPrio);
    }

    @Override
    public void addToList() {
        listeThemen.clear();
        meldungStart();
        addDay();
        if (Config.getStop()) {
            meldungThreadUndFertig();
        } else if (listeThemen.isEmpty() && listeTage.isEmpty()) {
            meldungThreadUndFertig();
        } else {
            meldungAddMax(listeThemen.size() + listeTage.size());
            //alles auswerten
            for (int t = 0; t < maxThreadLaufen; ++t) {
                //new Thread(new ThemaLaden()).start();
                Thread th = new Thread(new ThemaLaden());
                th.setName(SENDERNAME + t);
                th.start();
            }
        }
    }

    private void addDay() {
        //https://www.zdf.de/sendung-verpasst?airtimeDate=2016-10-26
        String date;
        for (long i = 0; i < (Config.loadLongMax() ? 300 : 20); ++i) {
            date = new SimpleDateFormat("yyyy-MM-dd").format(new Date().getTime() - i * (1000 * 60 * 60 * 24));
            String url = "https://www.zdf.de/sendung-verpasst?airtimeDate=" + date;
            listeTage.addUrl(new String[]{url});
        }
    }

    private class ThemaLaden implements Runnable {

        private final GetUrl getUrl = new GetUrl(wartenSeiteLaden);
        private MSStringBuilder seite1 = new MSStringBuilder(Const.STRING_BUFFER_START_BUFFER);
        private MSStringBuilder seite2 = new MSStringBuilder(Const.STRING_BUFFER_START_BUFFER);
        MSStringBuilder seiteDay = new MSStringBuilder(Const.STRING_BUFFER_START_BUFFER);
        private final ArrayList<String> urlList = new ArrayList<>();

        @Override
        public void run() {
            try {
                String link[];
                meldungAddThread();
                while (!Config.getStop() && (link = listeTage.getListeThemen()) != null) {
                    seite1.setLength(0);
                    getUrlsDay(link[0]/* url */);
                    meldungProgress(link[0]);
                }
            } catch (Exception ex) {
                Log.errorLog(496583200, ex);
            }
            meldungThreadUndFertig();
        }

        private void getUrlsDay(String url) {
            final String MUSTER_URL = "data-plusbar-url=\"";
            ArrayList<String> urls = new ArrayList<>();
            meldung(url);
            seiteDay = getUrl.getUri(SENDERNAME, url, Const.KODIERUNG_UTF, 2 /* versuche */, seiteDay, "" /* Meldung */);
            if (seiteDay.length() == 0) {
                Log.errorLog(942031254, "Leere Seite für URL: " + url);
                return;
            }
            seiteDay.extractList(MUSTER_URL, "\"", urls);
            for (String u : urls) {
                if (Config.getStop()) {
                    break;
                }
                addFilmePage(u);
            }
        }

        private void addFilmePage(String url) {
            try {
                seite1 = getUrl.getUri(SENDERNAME, url, Const.KODIERUNG_UTF, 1 /* versuche */, seite1, "" /* Meldung */);
                if (seite1.length() == 0) {
                    Log.errorLog(945120365, "Leere Seite für URL: " + url);
                    return;
                }
                String apiToken = seite1.extract("\"config\": \"", "\"");
                String filmUrl = seite1.extract("\"content\": \"", "\"");
                if (apiToken.isEmpty() || filmUrl.isEmpty()) {
                    Log.errorLog(461203789, "leeres token: " + url);
                }
                //apiToken = getToken("https://www.zdf.de" + apiToken);
                apiToken = "d2726b6c8c655e42b68b0db26131b15b22bd1a32";

                String thema = seite1.extract("<span class=\"teaser-cat\">", "<").trim();
                String titel = seite1.extract("<title>", "<"); //<title>Kielings wilde Welt (1/3) - ZDFmediathek</title>
                titel = titel.replace("- ZDFmediathek", "").trim();
                if (thema.isEmpty()) {
                    thema = titel;
                }
                if (thema.contains("|")) {
                    thema = thema.substring(thema.indexOf("|") + 1).trim();
                    //thema = thema.substring(0, thema.indexOf("|")).trim();
                } else {
                    titel = thema;
                }
                String dauer = seite1.extract("<dd class=\"video-duration defdesc m-border\">", "<");
                dauer = dauer.replace("min", "").trim();
                long duration = 0;
                try {
                    if (!dauer.equals("")) {
                        duration = Long.parseLong(dauer) * 60;
                    }
                } catch (NumberFormatException ex) {
                    Log.errorLog(462310178, ex, url);
                }

                //<time datetime="2016-10-29T16:15:00.000+02:00">29.10.2016</time>
                String date = seite1.extract("<time datetime=\"", "\"");
                String time = convertTime(date);
                date = convertDate(date);

                String description = seite1.extract("<p class=\"item-description\" >", "<").trim();
                addFilmeJson(filmUrl, url, apiToken, thema, titel, duration, date, time, description);
            } catch (Exception ex) {
                Log.errorLog(642130547, ex, url);
            }
        }

        private void addFilmeJson(String url, String urlSendung, String token, String thema, String titel,
                long duration, String date, String time, String description) {
            seite2 = getUrl.getUri(SENDERNAME, url, Const.KODIERUNG_UTF, 1 /* versuche */, seite2, "" /* Meldung */, token);
            if (seite2.length() == 0) {
                Log.errorLog(945120365, "Leere Seite für URL: " + url);
                return;
            }

            String s1 = seite2.extract(",\"uurl\":\"", "\"");
            if (s1.isEmpty()) {
                Log.errorLog(915263698, "Leere Seite für URL: " + url);
                return;
            }

            s1 = "https://api.zdf.de//tmd/2/portal/vod/ptmd/mediathek/" + s1;
//            String s2 = s1;

            JsonNode filmNode = readUrl(s1, token);
            if(filmNode == null) {
                Log.errorLog(721548963, "Leere Seite für URL: " + url);
                return;
            }
            
            String subTitle = getSubTitleUrl(filmNode);
            String[] filmUrls = getFilmUrls(filmNode);

            String urlNormal = getNormalUrl(filmUrls);
            if (urlNormal == null) {
                Log.errorLog(642130547, "Keine FilmURL: " + url);
            } else {
                DatenFilm film = new DatenFilm(SENDERNAME, thema, urlSendung /*urlThema*/, titel, urlNormal, "" /*urlRtmp*/,
                        date, time, duration, description);
                urlTauschen(film, urlSendung, mSearchFilmeSuchen);
                addFilm(film);
                if(filmUrls[4] != null) {
                    film.addUrlHd(filmUrls[4], "");
                }
                
                String urlLow = getLowUrl(filmUrls);
                if (urlLow != null && urlLow != urlNormal) {
                    film.addUrlKlein(urlLow, "");
                }
                if (!subTitle.isEmpty()) {
                    film.addUrlSubtitle(subTitle);
                }
            }
        }

        private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");//2016-10-29T16:15:00.000+02:00
        private final SimpleDateFormat sdfOutTime = new SimpleDateFormat("HH:mm:ss");
        private final SimpleDateFormat sdfOutDay = new SimpleDateFormat("dd.MM.yyyy");

        private String convertDate(String datum) {
            try {
                Date filmDate = sdf.parse(datum);
                datum = sdfOutDay.format(filmDate);
            } catch (ParseException ex) {
                Log.errorLog(731025789, ex, "Datum: " + datum);
            }
            return datum;
        }

        private String convertTime(String zeit) {
            try {
                Date filmDate = sdf.parse(zeit);
                zeit = sdfOutTime.format(filmDate);
            } catch (ParseException ex) {
                Log.errorLog(915423687, ex, "Time: " + zeit);
            }
            return zeit;
        }

    } 
    
    private JsonNode readUrl(String url, String token) {
        Client c = ClientBuilder.newClient().register(JacksonJsonProvider.class);
        WebTarget webTarget = c.target(url);

        Invocation.Builder invocationBuilder = webTarget.request(MediaType.APPLICATION_JSON_TYPE);
        invocationBuilder.header("Api-Auth", "Bearer " + token);
        
        Response response = invocationBuilder.get();
        if(response.getStatus() == 200) {
            JsonNode value = response.readEntity(JsonNode.class);
            return value;        
        }

        Log.errorLog(915423699, "loading url " + url + " failed with status code " + response.getStatus());        
        return null;
    }
   
    public String getSubTitleUrl(JsonNode filmNode) {
        String subTitleUrl = "";
        
        Iterator<JsonNode> captions = filmNode.withArray("captions").elements();
        while(captions.hasNext()) {
            JsonNode caption = captions.next();
            
            String url = caption.get("uri").textValue();
            
            // xml-Untertitel werden bevorzugt
            if (url.endsWith(".xml")) {
                subTitleUrl = url;
            } else if (subTitleUrl.isEmpty()) {
                subTitleUrl = url;
            }
        }
        
        return subTitleUrl;
    }
    
    public String[] getFilmUrls(JsonNode filmNode) {
        String[] urls = new String[5];
        
        Iterator<JsonNode> priorityList = filmNode.withArray("priorityList").elements();
        while(priorityList.hasNext()) {
            JsonNode priority = priorityList.next();
            
            Iterator<JsonNode> formitaeten = priority.withArray("formitaeten").elements();
            while(formitaeten.hasNext()){
                JsonNode formitaet = formitaeten.next();

                String mimeType = formitaet.get("mimeType").textValue();
                if(mimeType.equalsIgnoreCase("video/mp4")) {
                    
                    Iterator<JsonNode> qualities = formitaet.withArray("qualities").elements();
                    while(qualities.hasNext()) {
                        JsonNode quality = qualities.next();

                        JsonNode url = quality.get("audio").withArray("tracks").elements().next();
                        
                        urls[getFilmTypeIndex(quality)] = url.get("uri").textValue();
                    }
                }
            }
        }
        return urls;
    }
    
    public String getNormalUrl(String[] filmUrls) {
        for(int i = 3; i >= 0; i--) {
            if(filmUrls[i] != null) {
                return filmUrls[i];
            }
        }
        
        return null;
    }
    
    public String getLowUrl(String[] filmUrls) {
        for(int i = 2; i >= 0; i--) {
            if(filmUrls[i] != null) {
                return filmUrls[i];
            }
        }
        
        return null;
    }
        
    // Index: 0=low, 1=medium, 2=high, 3=very high, 4=hd
    private int getFilmTypeIndex(JsonNode quality) {
        int index = 0;
        
        boolean isHd = quality.get("hd").asBoolean();
        if(isHd) {
            index = 4;
        }
        else {
            String qualityValue = quality.get("quality").asText();
            switch(qualityValue) {
                case "low":
                    index = 0;
                    break;
                case "med":
                    index = 1;
                    break;
                case "high":
                    index = 2;
                    break;
                case "veryhigh":
                    index = 3;
                    break;
                case "hd":
                    index = 4;
                    break;
                default:
                    Log.errorLog(915423688, "quality " + qualityValue + " unknown.");
            }
        }
        
        return index;
    }
    
    public static void urlTauschen(DatenFilm film, String urlSeite, FilmeSuchen mSFilmeSuchen) {
        // manuell die Auflösung hochsetzen
        changeUrl("1456k_p13v11.mp4", "2256k_p14v11.mp4", film, urlSeite, mSFilmeSuchen);
        changeUrl("1456k_p13v12.mp4", "2256k_p14v12.mp4", film, urlSeite, mSFilmeSuchen);

        // manuell die Auflösung für HD setzen
        updateHd("1496k_p13v13.mp4", "3328k_p36v13.mp4", film, urlSeite);
        updateHd("2256k_p14v12.mp4", "3256k_p15v12.mp4", film, urlSeite);
        updateHd("2328k_p35v12.mp4", "3328k_p36v12.mp4", film, urlSeite);
    }

    private static void changeUrl(String from, String to, DatenFilm film, String urlSeite, FilmeSuchen mSFilmeSuchen) {
        if (film.arr[DatenFilm.FILM_URL].endsWith(from)) {
            String url_ = film.arr[DatenFilm.FILM_URL].substring(0, film.arr[DatenFilm.FILM_URL].lastIndexOf(from)) + to;
            String l = mSFilmeSuchen.listeFilmeAlt.getFileSizeUrl(url_, film.arr[DatenFilm.FILM_SENDER]);
            // zum Testen immer machen!!
            if (!l.isEmpty()) {
                film.arr[DatenFilm.FILM_GROESSE] = l;
                film.arr[DatenFilm.FILM_URL] = url_;
            } else if (urlExists(url_)) {
                // dann wars wohl nur ein "403er"
                film.arr[DatenFilm.FILM_URL] = url_;
            } else {
                Log.errorLog(945120369, "urlTauschen: " + urlSeite);
            }
        }
    }

    private static void updateHd(String from, String to, DatenFilm film, String urlSeite) {
        if (film.arr[DatenFilm.FILM_URL_HD].isEmpty() && film.arr[DatenFilm.FILM_URL].endsWith(from)) {
            String url_ = film.arr[DatenFilm.FILM_URL].substring(0, film.arr[DatenFilm.FILM_URL].lastIndexOf(from)) + to;
            // zum Testen immer machen!!
            if (urlExists(url_)) {
                film.addUrlHd(url_, "");
            } else {
                Log.errorLog(945120147, "urlTauschen: " + urlSeite);
            }
        }
    }

    public static DatenFilm filmHolenId(GetUrl getUrl, MSStringBuilder strBuffer, String sender, String thema, String titel, String filmWebsite, String urlId) {
        //<teaserimage alt="Harald Lesch im Studio von Abenteuer Forschung" key="298x168">http://www.zdf.de/ZDFmediathek/contentblob/1909108/timg298x168blob/8081564</teaserimage>
        //<detail>Möchten Sie wissen, was Sie in der nächsten Sendung von Abenteuer Forschung erwartet? Harald Lesch informiert Sie.</detail>
        //<length>00:00:34.000</length>
        //<airtime>02.07.2013 23:00</airtime>
        final String BESCHREIBUNG = "<detail>";
        final String LAENGE_SEC = "<lengthSec>";
        final String LAENGE = "<length>";
        final String DATUM = "<airtime>";
        final String THEMA = "<originChannelTitle>";
        long laengeL;

        String beschreibung, subtitle, laenge, datum, zeit = "";

        strBuffer = getUrl.getUri_Utf(sender, urlId, strBuffer, "URL-Filmwebsite: " + filmWebsite);
        if (strBuffer.length() == 0) {
            Log.errorLog(398745601, "url: " + urlId);
            return null;
        }

        subtitle = strBuffer.extract("<caption>", "<url>http://", "<", "http://");
        if (subtitle.isEmpty()) {
            subtitle = strBuffer.extract("<caption>", "<url>https://", "<", "https://");
//            if (!subtitle.isEmpty()) {
//                System.out.println("Hallo");
//            }
        }
        beschreibung = strBuffer.extract(BESCHREIBUNG, "<");
        if (beschreibung.isEmpty()) {
            beschreibung = strBuffer.extract(BESCHREIBUNG, "</");
            beschreibung = beschreibung.replace("<![CDATA[", "");
            beschreibung = beschreibung.replace("]]>", "");
            if (beschreibung.isEmpty()) {
                Log.errorLog(945123074, "url: " + urlId);
            }
        }
        if (thema.isEmpty()) {
            thema = strBuffer.extract(THEMA, "<");
        }

        laenge = strBuffer.extract(LAENGE_SEC, "<");
        if (!laenge.isEmpty()) {
            laengeL = extractDurationSec(laenge);
        } else {
            laenge = strBuffer.extract(LAENGE, "<");
            if (laenge.contains(".")) {
                laenge = laenge.substring(0, laenge.indexOf("."));
            }
            laengeL = extractDuration(laenge);
        }

        datum = strBuffer.extract(DATUM, "<");
        if (datum.contains(" ")) {
            zeit = datum.substring(datum.lastIndexOf(" ")).trim() + ":00";
            datum = datum.substring(0, datum.lastIndexOf(" ")).trim();
        }

        //============================================================================
        // und jetzt die FilmURLs
        final String[] QU_WIDTH_HD = {"1280"};
        final String[] QU_WIDTH = {"1024", "852", "720", "688", "480", "432", "320"};
        final String[] QU_WIDTH_KL = {"688", "480", "432", "320"};
        String url, urlKlein, urlHd, tmp = "";

        urlHd = getUrl(strBuffer, QU_WIDTH_HD, tmp, true);
        url = getUrl(strBuffer, QU_WIDTH, tmp, true);
        urlKlein = getUrl(strBuffer, QU_WIDTH_KL, tmp, false);

        if (url.equals(urlKlein)) {
            urlKlein = "";
        }
        if (url.isEmpty()) {
            url = urlKlein;
            urlKlein = "";
        }

        //===================================================
        if (urlHd.isEmpty()) {
//            MSLog.fehlerMeldung(912024587, "keine URL: " + filmWebsite);
        }
        if (urlKlein.isEmpty()) {
//            MSLog.fehlerMeldung(310254698, "keine URL: " + filmWebsite);
        }
        if (url.isEmpty()) {
            Log.errorLog(397002891, "keine URL: " + filmWebsite);
            return null;
        } else {
            DatenFilm film = new DatenFilm(sender, thema, filmWebsite, titel, url, "" /*urlRtmp*/, datum, zeit,
                    laengeL, beschreibung);
            if (!subtitle.isEmpty()) {
                film.addUrlSubtitle(subtitle);
            }
            film.addUrlKlein(urlKlein, "");
            film.addUrlHd(urlHd, "");
            return film;
        }
    }

    private static String getUrl(MSStringBuilder strBuffer, String[] arr, String tmp, boolean hd) {
        final String URL_ANFANG = "<formitaet basetype=\"h264_aac_mp4_http_na_na\"";
        final String URL_ENDE = "</formitaet>";
        final String URL = "<url>";
        final String WIDTH = "<width>";

        String ret = "";
        tmp = "";
        int posAnfang, posEnde;
        mainloop:
        for (String qual : arr) {
            posAnfang = 0;
            while (true) {
                if ((posAnfang = strBuffer.indexOf(URL_ANFANG, posAnfang)) == -1) {
                    break;
                }
                posAnfang += URL_ANFANG.length();
                if ((posEnde = strBuffer.indexOf(URL_ENDE, posAnfang)) == -1) {
                    break;
                }

                tmp = strBuffer.extract(URL, "<", posAnfang, posEnde);
                if (strBuffer.extract(WIDTH, "<", posAnfang, posEnde).equals(qual)) {
                    if (hd) {
                        ret = checkUrlHD(tmp);
                    } else {
                        ret = checkUrl(tmp);
                    }
                    if (!ret.isEmpty()) {
                        break mainloop;
                    }
                }
            }
        }
        if (ret.startsWith("http://tvdl.zdf.de")) {
            ret = ret.replace("http://tvdl.zdf.de", "http://nrodl.zdf.de");
        }
        return ret;
    }

    private static String checkUrlHD(String url) {
        String ret = "";
        if (url.startsWith("http") && url.endsWith("mp4")) {
            ret = url;
            if (ret.startsWith("http://www.metafilegenerator.de/ondemand/zdf/hbbtv/")) {
                ret = ret.replaceFirst("http://www.metafilegenerator.de/ondemand/zdf/hbbtv/", "http://nrodl.zdf.de/");
            }
        }
        return ret;
    }

    private static String checkUrl(String url) {
        String ret = "";
        if (url.startsWith("http") && url.endsWith("mp4")) {
            if (!url.startsWith("http://www.metafilegenerator.de/")) {
                ret = url;
            }
        }
        return ret;
    }

}
