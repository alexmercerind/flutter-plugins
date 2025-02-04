import 'dart:async';

import 'package:flutter/services.dart';
import 'dart:io' show Platform;

const int _stopped = 0, _walking = 1;

class Pedometer {
  static const EventChannel _stepDetectionChannel =
      const EventChannel('step_detection');
  static const EventChannel _stepCountChannel =
      const EventChannel('step_count');

  static StreamController<PedestrianStatus> _androidPedestrianController =
      StreamController.broadcast();

  /// Returns one step at a time. Events come every time a step is detected.
  static Stream<PedestrianStatus> get pedestrianStatusStream {
    Stream<PedestrianStatus> stream = _stepDetectionChannel
        .receiveBroadcastStream()
        .map((event) => PedestrianStatus._(event));
    if (Platform.isAndroid) return _androidStream(stream);
    return stream;
  }

  /// Transformed stream for the Android platform
  static Stream<PedestrianStatus> _androidStream(
      Stream<PedestrianStatus> stream) {
    // Init a timer and a status
    Timer? t;
    int? pedestrianStatus;
    // Listen for events on the original stream. Transform these events by using the timer.
    stream.listen((PedestrianStatus e) {
      // Android's accelerometer based fallback.
      if (e.fallback) {
        _androidPedestrianController.add(e);
        return;
      }
      // Default implementation.

      // If an event is received it means the status is 'walking'.
      // If the timer has been started, it should be cancelled to prevent sending out additional 'walking' events.
      if (t != null) {
        t!.cancel();
        // If a previous status was either not set yet, or was 'stopped'
        // then a 'walking' event should be emitted.
        if (pedestrianStatus == null || pedestrianStatus == _stopped) {
          _androidPedestrianController.add(PedestrianStatus._(_walking));
          pedestrianStatus = _walking;
        }
      }
      // After receiving an event, start a timer for 2 seconds, after which a 'stopped' event is emitted.
      // If it manages to go through, it is because no events were received for the 2 second duration
      t = Timer(Duration(seconds: 2), () {
        _androidPedestrianController.add(PedestrianStatus._(_stopped));
        pedestrianStatus = _stopped;
      });
    });
    return _androidPedestrianController.stream;
  }

  /// Returns the steps taken since last system boot. Events may come with a delay.
  static Stream<StepCount> get stepCountStream => _stepCountChannel
      .receiveBroadcastStream()
      .map((event) => StepCount._(event));
}

/// A DTO for steps taken containing the number of steps taken.
class StepCount {
  late DateTime _timeStamp;
  int _steps = 0;

  StepCount._(dynamic e) {
    _steps = e as int;
    _timeStamp = DateTime.now();
  }

  int get steps => _steps;

  DateTime get timeStamp => _timeStamp;

  @override
  String toString() =>
      'Steps taken: $_steps at ${_timeStamp.toIso8601String()}';
}

/// A DTO for steps taken containing a detected step and its corresponding status, i.e. walking, stopped or unknown.
class PedestrianStatus {
  final String status;
  final bool fallback;
  final DateTime timeStamp;

  static const _WALKING = 'walking';
  static const _STOPPED = 'stopped';
  static const _UNKNOWN = 'unknown';

  static const Map<int, String> _STATUSES = {
    _stopped: _STOPPED,
    _walking: _WALKING,
    // Android accelerometer fallback.
    -1: _STOPPED,
    -2: _WALKING,
  };

  PedestrianStatus._(int t)
      : status = _STATUSES[t]!,
        fallback = t < 0,
        timeStamp = DateTime.now();

  @override
  String toString() => 'Status: $status at ${timeStamp.toIso8601String()}';
}
