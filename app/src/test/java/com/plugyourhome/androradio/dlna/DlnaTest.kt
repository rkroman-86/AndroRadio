package com.plugyourhome.androradio.dlna

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.plugyourhome.androradio.data.Station
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Simule un lecteur DLNA (description proche d'un Arylic / LinkPlay) et vérifie les échanges. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DlnaTest {

    private lateinit var server: MockWebServer
    private val client = OkHttpClient()

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private val description = """<?xml version="1.0"?>
        <root xmlns="urn:schemas-upnp-org:device-1-0">
          <specVersion><major>1</major><minor>0</minor></specVersion>
          <device>
            <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
            <friendlyName>Arylic LA50 7165be</friendlyName>
            <manufacturer>Linkplay</manufacturer>
            <UDN>uuid:FF31F0A4-1234-5678-9abc-7165be000000</UDN>
            <serviceList>
              <service>
                <serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType>
                <serviceId>urn:upnp-org:serviceId:RenderingControl</serviceId>
                <controlURL>/upnp/control/rendercontrol1</controlURL>
              </service>
              <service>
                <serviceType>urn:schemas-upnp-org:service:ConnectionManager:1</serviceType>
                <controlURL>/upnp/control/rendercmngr1</controlURL>
              </service>
              <service>
                <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
                <serviceId>urn:upnp-org:serviceId:AVTransport</serviceId>
                <controlURL>/upnp/control/rendertransport1</controlURL>
              </service>
            </serviceList>
          </device>
        </root>"""

    private fun soap(body: String) = MockResponse().setBody(
        """<?xml version="1.0"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body>$body</s:Body></s:Envelope>""",
    )

    @Test
    fun describe_readsRendererServices() {
        server.enqueue(MockResponse().setBody(description))
        val discovery = DlnaDiscovery(ApplicationProvider.getApplicationContext(), client)

        val device = discovery.describe(server.url("/description.xml").toString())

        assertNotNull(device)
        device!!
        assertEquals("Arylic LA50 7165be", device.name)
        assertEquals("uuid:FF31F0A4-1234-5678-9abc-7165be000000", device.id)
        assertEquals(server.url("/upnp/control/rendertransport1").toString(), device.avTransportUrl)
        assertEquals(server.url("/upnp/control/rendercontrol1").toString(), device.renderingControlUrl)
    }

    @Test
    fun describe_ignoresDeviceWithoutTransport() {
        server.enqueue(MockResponse().setBody("""<root><device><deviceType>urn:x:device:Router:1</deviceType><friendlyName>Box</friendlyName></device></root>"""))
        val discovery = DlnaDiscovery(ApplicationProvider.getApplicationContext(), client)
        assertNull(discovery.describe(server.url("/d.xml").toString()))
    }

    @Test
    fun play_sendsStreamThenPlay() = runBlocking {
        repeat(3) { server.enqueue(soap("<u:Ok/>")) } // Stop, SetAVTransportURI, Play
        val device = DlnaDevice("id", "Test", server.url("/av").toString(), server.url("/rc").toString())
        val station = Station(
            uuid = "s1",
            name = "Jazz & Co <live>",
            urlResolved = "http://stream.example.com/jazz?x=1&y=2",
            codec = "AAC",
        )

        DlnaRenderer(device, client).play(station)

        val stop = server.takeRequest()
        assertEquals("\"urn:schemas-upnp-org:service:AVTransport:1#Stop\"", stop.getHeader("SOAPACTION"))

        val set = server.takeRequest()
        assertEquals("\"urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI\"", set.getHeader("SOAPACTION"))
        val body = set.body.readUtf8()
        // L'adresse du flux est échappée une fois dans CurrentURI…
        assertTrue(body.contains("<CurrentURI>http://stream.example.com/jazz?x=1&amp;y=2</CurrentURI>"))
        // …et les métadonnées DIDL sont échappées comme texte, avec le bon type audio.
        assertTrue(body.contains("&lt;DIDL-Lite"))
        assertTrue(body.contains("audio/aac"))
        assertTrue(body.contains("Jazz &amp;amp; Co &amp;lt;live&amp;gt;"))

        val play = server.takeRequest()
        assertEquals("\"urn:schemas-upnp-org:service:AVTransport:1#Play\"", play.getHeader("SOAPACTION"))
        assertTrue(play.body.readUtf8().contains("<Speed>1</Speed>"))
    }

    @Test
    fun readsStateTitleAndVolume() = runBlocking {
        val device = DlnaDevice("id", "Test", server.url("/av").toString(), server.url("/rc").toString())
        val renderer = DlnaRenderer(device, client)

        server.enqueue(soap("<u:GetTransportInfoResponse><CurrentTransportState>PLAYING</CurrentTransportState></u:GetTransportInfoResponse>"))
        assertEquals(RemoteTransport.PLAYING, renderer.transport())

        val meta = "&lt;DIDL-Lite&gt;&lt;item&gt;&lt;dc:title&gt;Midnight Ferris Wheel&lt;/dc:title&gt;" +
            "&lt;upnp:artist&gt;Luna Harbor&lt;/upnp:artist&gt;&lt;/item&gt;&lt;/DIDL-Lite&gt;"
        server.enqueue(soap("<u:GetPositionInfoResponse><Track>1</Track><TrackMetaData>$meta</TrackMetaData></u:GetPositionInfoResponse>"))
        assertEquals("Luna Harbor - Midnight Ferris Wheel", renderer.nowPlaying())

        server.enqueue(soap("<u:GetVolumeResponse><CurrentVolume>42</CurrentVolume></u:GetVolumeResponse>"))
        assertEquals(0.42f, renderer.volume()!!, 0.001f)

        server.enqueue(soap("<u:SetVolumeResponse/>"))
        renderer.setVolume(0.7f)
        repeat(3) { server.takeRequest() }
        val setVolume = server.takeRequest().body.readUtf8()
        assertTrue(setVolume.contains("<DesiredVolume>70</DesiredVolume>"))
    }
}
