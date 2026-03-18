/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

/*!
 * Player JS caching wrapper for yt.solver.core.js
 *
 * This wrapper maintains player JS state in memory to avoid:
 * 1. Repeated JSON serialization/deserialization of large player JS
 * 2. Repeated V8 parsing and bytecode generation for the same player
 * 3. Repeated preprocessing of player JS by jsc()
 */
var _playerCache = {
    playerHash: null,
    playerJs: null,
    isPreprocessed: false
};

/**
 * Set player JS code with a hash for cache validation.
 * Call this before calling jscw() to ensure the player is available.
 *
 * @param {string} playerJS - The raw player JavaScript code
 * @param {string} playerHash - A hash of the player JS for cache validation
 * @param {boolean} [isPreprocessed=false] - Whether the provided player JS is already preprocessed
 */
function setPlayer(playerJS, playerHash, isPreprocessed) {
    if (_playerCache.playerHash !== playerHash || isPreprocessed !== _playerCache.isPreprocessed) {
        _playerCache.playerHash = playerHash;
        _playerCache.playerJs = playerJS;
        _playerCache.isPreprocessed = isPreprocessed;

        if (!isPreprocessed) {
            // If the player is not preprocessed, we will preprocess it on the first jscw() call
            jscw({requests: [{type: 'n', challenges: []}]})
        }
    }
}

/**
 * Clear the player cache. Call this when switching players or cleaning up.
 */
function clearPlayerCache() {
    _playerCache.playerHash = null;
    _playerCache.playerJs = null;
    _playerCache.isPreprocessed = false;
}

/**
 * Get the current player hash, or null if no player is loaded.
 *
 * @returns {string|null} The current player hash
 */
function getPlayerHash() {
    return _playerCache.playerHash;
}

/**
 * Get the current player JS, or null if no player is loaded.

 * @returns {string|null} The current player JavaScript code
 */
function getPlayerJs() {
    return _playerCache.playerJs;
}

function isPlayerPreprocessed() {
    return _playerCache.isPreprocessed;
}

/**
 * Check if the player with the given hash is already loaded and preprocessed.
 *
 * @param {string} playerHash - The hash to check
 * @returns {boolean} True if the player is loaded and preprocessed
 */
function isPlayerReady(playerHash) {
    return _playerCache.playerHash === playerHash && _playerCache.isPreprocessed;
}

/**
 * Wrapped jsc() function that uses the cached player JS.
 *
 * Input format:
 * {
 *   "requests": [
 *     {"type": "n" | "sig", "challenges": ["challenge1", "challenge2", ...]}
 *   ],
 *   "output_preprocessed": boolean (optional, defaults to true if player not preprocessed)
 * }
 *
 * @param {object} input - The input object containing requests
 * @returns {object} The solver output with responses and optionally preprocessed_player
 */
function jscw(input) {
    var playerJs = getPlayerJs();
    if (!playerJs) {
        return {
            type: "error",
            error: "No player loaded. Call setPlayer() first."
        };
    }

    // Build the input for the underlying jsc() function
    var jscInput;
    if (isPlayerPreprocessed()) {
        jscInput = {
            type: "preprocessed",
            preprocessed_player: playerJs,
            requests: input.requests
        };
    } else {
        jscInput = {
            type: "player",
            player: playerJs,
            requests: input.requests,
            output_preprocessed: true
        };
    }

    // Call the underlying jsc() function
    var output = jsc(jscInput);

    // If we got preprocessed player back, cache it
    if (output.preprocessed_player && !isPlayerPreprocessed()) {
        setPlayer(output.preprocessed_player, getPlayerHash(), true);
    }

    return output;
}

// Export for testing
if (typeof module !== 'undefined' && module.exports) {
    module.exports = {
        setPlayer: setPlayer,
        clearPlayerCache: clearPlayerCache,
        getPlayerHash: getPlayerHash,
        isPlayerReady: isPlayerReady,
        jscw: jscw
    };
}
