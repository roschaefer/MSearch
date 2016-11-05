/*
 * MediathekView
 * Copyright (C) 2014 W. Xaver
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import mSearch.filmeSuchen.FilmeSuchen;
import mSearch.filmeSuchen.sender.MediathekZdf;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author peter
 */
public class MediathekZdfTest {
    
    private MediathekZdf target;
    
    public MediathekZdfTest() {
    }
    
    @BeforeClass
    public static void setUpClass() {
    }
    
    @AfterClass
    public static void tearDownClass() {
    }
    
    @Before
    public void setUp() {
        FilmeSuchen search = new FilmeSuchen();
        target = new MediathekZdf(search, 1);
    }
    
    @After
    public void tearDown() {
    }

    private JsonNode readJsonResource(String jsonFileName) throws IOException {
        ObjectMapper m = new ObjectMapper();
        
        URL url = this.getClass().getResource(jsonFileName);
        File file = new File(url.getFile());
        return m.readTree(file);        
    }
    
    @Test
    public void getSubtitleUrlTestShouldReturnXmlUrl() throws IOException {
        String expected = "https://utstreaming.zdf.de/mtt/zdf/11/05/110516_dvoland_tex/2/Deutschland_von_oben2_Land_220511.xml";
        
        String actual = target.getSubTitleUrl(readJsonResource("/zdf_film_with_subtitle.json"));
        
        Assert.assertEquals(expected, actual);
    }       
    
    @Test
    public void getSubtitleUrlTestShouldNoUrl() throws IOException {
        String expected = "";
        
        String actual = target.getSubTitleUrl(readJsonResource("/zdf_film_sample.json"));
        
        Assert.assertEquals(expected, actual);
    }  
    
    @Test
    public void getFilmUrlsTestShouldReturnThreeFilms() throws IOException {
        String[] expected = {
                null,
                null,
                "https://downloadzdf-a.akamaihd.net/mp4/zdf/16/09/160906_sendung_dan/2/160906_sendung_dan_436k_p9v12.mp4",
                "https://downloadzdf-a.akamaihd.net/mp4/zdf/16/09/160906_sendung_dan/2/160906_sendung_dan_1456k_p13v12.mp4",
                "http://download.zdf.de/mp4/zdf/16/09/160906_sendung_dan/2/160906_sendung_dan_3256k_p15v12.mp4"
        };        

        String[] actual = target.getFilmUrls(readJsonResource("/zdf_film_sample.json"));
        
        Assert.assertArrayEquals(expected, actual);
    }
}
