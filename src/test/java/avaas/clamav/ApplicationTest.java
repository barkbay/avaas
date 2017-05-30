/******************************************************************************
 **
 ** This library is free software; you can redistribute it and/or
 ** modify it under the terms of the GNU Lesser General Public
 ** License as published by the Free Software Foundation; either
 ** version 2.1 of the License, or (at your option) any later version.
 **
 ** This library is distributed in the hope that it will be useful,
 ** but WITHOUT ANY WARRANTY; without even the implied warranty of
 ** MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 ** Lesser General Public License for more details.
 **
 ** You should have received a copy of the GNU Lesser General Public
 ** License along with this library; if not, write to the Free Software
 ** Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 *********************************************************************************/

package avaas.clamav;

import static org.junit.Assert.*;

import avaas.clamav.client.Application;
import org.junit.Test;

import java.util.regex.Matcher;

public class ApplicationTest {

    @Test
    public void hostnameTest() throws Exception {
        final String hostname = "clamav-rest-dev-9-rpblx";
        final Matcher matcher = Application.HostnamePattern.matcher(hostname);
        assertTrue(matcher.matches());
        assertEquals("clamav-rest", matcher.group(1));
        assertEquals("dev", matcher.group(2));
        assertEquals("9", matcher.group(3));
    }

    @Test
    public void hostnameTest2() throws Exception {
        final String hostname = "clamav-dev-11-rpblx";
        final Matcher matcher = Application.HostnamePattern.matcher(hostname);
        assertTrue(matcher.matches());
        assertEquals("clamav", matcher.group(1));
        assertEquals("dev", matcher.group(2));
        assertEquals("11", matcher.group(3));
    }

}
