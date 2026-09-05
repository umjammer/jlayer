module jlayer {
	exports javazoom.jl.player;
	exports javazoom.jl.converter;
	exports javazoom.jl.decoder;
	exports javazoom.jl.player.advanced;

	requires java.desktop;
	requires java.logging;

	uses javazoom.jl.player.AudioDeviceFactory;
	provides javazoom.jl.player.AudioDeviceFactory with 
            javazoom.jl.player.JavaSoundAudioDeviceFactory,
            javazoom.jl.player.NullAudioDeviceFactory;
}
