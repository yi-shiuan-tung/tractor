import yourTurn from '../assets/audio/your_turn.mp3';
import victory from '../assets/audio/victory.mp3';
import defeat from '../assets/audio/defeat.mp3';
import background from '../assets/audio/background.mp3';

function slowlyStop(audio) {
    setTimeout(() => {
        audio.volume *= 0.97;
        if (audio.volume < 0.003) {
            audio.pause();
        } else {
            slowlyStop(audio);
        }
    }, 100);
}

const VOLUMES = [0, 0.1, 0.4, 1.0];

/**
 * Provides convenience functions to play all audio files.
 * The input volume is an integer from 0 to 3 (inclusive), which gets mapped to
 * an actual Audio volume in [0, 1] using the mapping above.
 */
export const getAudio = function () {
    const audio = new Audio();
    const longAudio = new Audio();

    var startVolume = 0.1;

    function play(audio, file, loop) {
        if (audio.duration > 0) {
            audio.pause();
        }
        if (startVolume > 0) {
            audio.src = file;
            audio.volume = startVolume;
            audio.currentTime = 0;
            audio.loop = loop;
            audio.play();
        }
    }

    return {
        prepare: () => {
            // On mobile, an explicit user action is required to play mp3s
            audio.play().then(() => audio.pause());
            longAudio.play().then(() => longAudio.pause());
        },
        setVolume: volume => {
            startVolume = VOLUMES[volume];
            audio.volume = startVolume;
            longAudio.volume = startVolume;
        },
        playYourTurn: () => play(audio, yourTurn, false),
        playVictory: () => play(longAudio, victory, false),
        playDefeat: () => play(longAudio, defeat, false),
        playBackground: () => play(longAudio, background, true),
        slowlyStopBackground: () => slowlyStop(longAudio),
    };
}
