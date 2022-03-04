/*
 * Copyright 2013-2014 Odysseus Software GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.telegram.messenger.audioinfo.mp3;

public enum ID3v1Genre {
	/*
	 * The following genres is defined in ID3v1 (0-79)
	 */
	Blues("Blues"),
	ClassicRock("Classic Rock"),
	Country("Country"),
	Dance("Dance"),
	Disco("Disco"),
	Funk("Funk"),
	Grunge("Grunge"),
	HipHop("Hip-Hop"),
	Jazz("Jazz"),
	Metal("Metal"),
	NewAge("New Age"),
	Oldies("Oldies"),
	Other("Other"),
	Pop("Pop"),
	RnB("R&B"),
	Rap("Rap"),
	Reggae("Reggae"),
	Rock("Rock"),
	Techno("Techno"),
	Industrial("Industrial"),
	Alternative("Alternative"),
	Ska("Ska"),
	DeathMetal("Death Metal"),
	Pranks("Pranks"),
	Soundtrack("Soundtrack"),
	EuroTechno("Euro-Techno"),
	Ambient("Ambient"),
	TripHop("Trip-Hop"),
	Vocal("Vocal"),
	JazzFunk("Jazz+Funk"),
	Fusion("Fusion"),
	Trance("Trance"),
	Classical("Classical"),
	Instrumental("Instrumental"),
	Acid("Acid"),
	House("House"),
	Game("Game"),
	SoundClip("Sound Clip"),
	Gospel("Gospel"),
	Noise("Noise"),
	AlternRock("AlternRock"),
	Bass("Bass"),
	Soul("Soul"),
	Punk("Punk"),
	Space("Space"),
	Meditative("Meditative"),
	InstrumentalPop("Instrumental Pop"),
	InstrumentalRock("Instrumental Rock"),
	Ethnic("Ethnic"),
	Gothic("Gothic"),
	Darkwave("Darkwave"),
	TechnoIndustrial("Techno-Industrial"),
	Electronic("Electronic"),
	PopFolk("Pop-Folk"),
	Eurodance("Eurodance"),
	Dream("Dream"),
	SouthernRock("Southern Rock"),
	Comedy("Comedy"),
	Cult("Cult"),
	Gangsta("Gangsta"),
	Top40("Top 40"),
	ChristianRap("Christian Rap"),
	PopFunk("Pop/Funk"),
	Jungle("Jungle"),
	NativeAmerican("Native American"),
	Cabaret("Cabaret"),
	NewWave("New Wave"),
	Psychadelic("Psychadelic"),
	Rave("Rave"),
	Showtunes("Showtunes"),
	Trailer("Trailer"),
	LoFi("Lo-Fi"),
	Tribal("Tribal"),
	AcidPunk("Acid Punk"),
	AcidJazz("Acid Jazz"),
	Polka("Polka"),
	Retro("Retro"),
	Musical("Musical"),
	RockAndRoll("Rock & Roll"),
	HardRock("Hard Rock"),

	/*
	 * The following genres are Winamp extensions (80-125)
	 */
	Folk("Folk"),
	FolkRock("Folk-Rock"),
	NationalFolk("National Folk"),
	Swing("Swing"),
	FastFusion("Fast Fusion"),
	Bebop("Bebop"),
	Latin("Latin"),
	Revival("Revival"),
	Celtic("Celtic"),
	Bluegrass("Bluegrass"),
	Avantgarde("Avantgarde"),
	GothicRock("Gothic Rock"),
	ProgressiveRock("Progressive Rock"),
	PsychedelicRock("Psychedelic Rock"),
	SymphonicRock("Symphonic Rock"),
	SlowRock("Slow Rock"),
	BigBand("Big Band"),
	Chorus("Chorus"),
	EasyListening("Easy Listening"),
	Acoustic("Acoustic"),
	Humour("Humour"),
	Speech("Speech"),
	Chanson("Chanson"),
	Opera("Opera"),
	ChamberMusic("Chamber Music"),
	Sonata("Sonata"),
	Symphony("Symphony"),
	BootyBass("Booty Bass"),
	Primus("Primus"),
	PornGroove("Porn Groove"),
	Satire("Satire"),
	SlowJam("Slow Jam"),
	Club("Club"),
	Tango("Tango"),
	Samba("Samba"),
	Folklore("Folklore"),
	Ballad("Ballad"),
	PowerBallad("Power Ballad"),
	RhytmicSoul("Rhythmic Soul"),
	Freestyle("Freestyle"),
	Duet("Duet"),
	PunkRock("Punk Rock"),
	DrumSolo("Drum Solo"),
	ACapella("A capella"),
	EuroHouse("Euro-House"),
	DanceHall("Dance Hall");

	public static ID3v1Genre getGenre(int id) {
		ID3v1Genre[] values = values();
		return id >= 0 && id < values.length ? values[id] : null;
	}

	private final String description;

	ID3v1Genre(String description) {
		this.description = description;
	}

	public String getDescription() {
		return description;
	}

	public int getId() {
		return ordinal();
	}
}
