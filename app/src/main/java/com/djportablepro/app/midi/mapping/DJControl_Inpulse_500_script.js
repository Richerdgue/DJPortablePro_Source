// DJControl_Inpulse_500_script.js
// Mixxx mapping script file for the Hercules DJControl Inpulse 500.
// Authors: Ev3nt1ne, DJ Phatso
// contributions by Kerrick Staley, resetreboot, Bentheshrubber, ThatOneRuffian
// Version 2.0a (Dec 2022)
// Forum: https://mixxx.discourse.group/t/hercules-djcontrol-inpulse-500/19739
// Wiki: https://mixxx.org/wiki/doku.php/hercules_djcontrol_inpulse_500

var DJCi500 = {};

// USER OPTIONS
DJCi500.scratchScale = 1.0;
DJCi500.scratchShiftMultiplier = 4;
DJCi500.bendScale = 1.0;
DJCi500.kScratchActionNone = 0;
DJCi500.kScratchActionScratch = 1;
DJCi500.kScratchActionSeek = 2;
DJCi500.kScratchActionBend = 3;
DJCi500.FxLedtimer = null;

DJCi500.FxD1Active = [0, 0, 0];
DJCi500.FxD2Active = [0, 0, 0];
DJCi500.FxDeckSel = 0;
DJCi500.prevFilterUse = [0, 0];
DJCi500.pitchRanges = [0.08, 0.32, 1];
DJCi500.pitchRangesId = [0, 0];
DJCi500.slowPauseSetState = [0, 0];

DJCi500.selectedSlicerDomain = [8, 8, 8, 8];
DJCi500.slicerBeatsPassed = [0, 0, 0, 0];
DJCi500.slicerPreviousBeatsPassed = [0, 0, 0, 0];
DJCi500.slicerTimer = [false, false, false, false];
DJCi500.slicerActive = [false, false, false, false];
DJCi500.slicerAlreadyJumped = [false, false, false, false];
DJCi500.slicerButton = [-1, -1, -1, -1];
DJCi500.slicerModes = {
    'contSlice': 0,
    'loopSlice': 1
};
DJCi500.activeSlicerMode = [
    DJCi500.slicerModes.contSlice,
    DJCi500.slicerModes.contSlice,
    DJCi500.slicerModes.contSlice,
    DJCi500.slicerModes.contSlice
];
DJCi500.slicerLoopBeat8 = [0, 0, 0, 0];

// Color Constants (RGB Hex to Inpulse 500 MIDI Byte)
DJCi500.ColorMap = {
    RED: 0x60,
    YELLOW: 0x7C,
    GREEN: 0x1C,
    CYAN: 0x1F,
    BLUE: 0x03,
    MAGENTA: 0x42,
    PINK: 0x63,
    WHITE: 0x7F,
    DARK_BLUE: 0x02,
    DARK_GREEN: 0x10,
    DARK_CYAN: 0x12,
    DARK_RED: 0x40,
    ORANGE: 0x74,
    DIM_WHITE: 0x52
};

DJCi500.xFaderScratch = 0;

DJCi500.vuMeterUpdateMaster = function(value, _group, control) {
    var v = Math.floor((value * 122) + 5);
    var ctrl = (control === "VuMeterL") ? 0x40 : 0x41;
    // midi.sendShortMsg(0xB0, ctrl, v);
};

DJCi500.vuMeterUpdateDeck = function(value, group, _control, _status) {
    var v = Math.floor((value * 122) + 5);
    var status = (group === "[Channel1]") ? 0xB1 : 0xB2;
    // midi.sendShortMsg(status, 0x40, v);
};
