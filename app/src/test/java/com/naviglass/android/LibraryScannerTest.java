package com.naviglass.android;
import org.junit.Test;
import static org.junit.Assert.*;
public class LibraryScannerTest {
    @Test public void localAudioExtensions(){for(String name:new String[]{"a.WAV","x.flac","x.mp3","x.m4a","x.aac","x.opus","x.ogg"})assertTrue(name,LibraryScanner.supported(name));}
    @Test public void archivesAndFakeSuffixesExcluded(){for(String name:new String[]{"album.zip","album.rar","song.mp3.exe","cover.jpg","song"})assertFalse(name,LibraryScanner.supported(name));}
    @Test public void trackNumberAndMissingMetadata(){assertEquals(12,LibraryScanner.number("12/16"));assertEquals(0,LibraryScanner.number(null));assertEquals("fallback",LibraryScanner.clean(" ","fallback"));assertEquals("fallback",LibraryScanner.clean("<unknown>","fallback"));assertEquals("Album",LibraryScanner.clean(" Album ","fallback"));}
    @Test public void stableSafeIdentifiers(){assertEquals(32,Library.key("content://test/track").length());assertEquals(Library.key("same"),Library.key("same"));assertNotEquals(Library.key("first"),Library.key("second"));}
}
