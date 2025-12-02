// src/hooks/useWebSpeech.ts
import { useEffect, useRef } from "react";

type ResultHandler = (text: string) => void;

export function useWebSpeech(onResult: ResultHandler) {
  const recogRef = useRef<SpeechRecognition | null>(null);
  const startedRef = useRef(false);

  useEffect(() => {
    const SpeechRec =
      (window as any).SpeechRecognition ||
      (window as any).webkitSpeechRecognition;
    if (!SpeechRec) {
      console.warn("Web Speech API non supportée");
      return;
    }

    const recog = new SpeechRec();
    recog.continuous = true;
    recog.interimResults = false;
    recog.lang = "en-US";

    recog.onstart = () => {
      startedRef.current = true;
      console.log("🟢 SpeechRecognition started");
    };
    recog.onresult = (ev: SpeechRecognitionEvent) => {
      const res = ev.results[ev.resultIndex];
      if (res.isFinal) {
        const text = res[0].transcript.trim();
        console.log("✅ Final transcript:", text);
        onResult(text);
      }
    };
    recog.onerror = (e: any) => {
      console.error("🔥 SpeechRecognition error:", e.error);
      if (e.error === "not-allowed" || e.error === "service-not-allowed") {
        startedRef.current = false;
      }
    };
    recog.onend = () => {
      console.log("🔴 SpeechRecognition ended");
      if (startedRef.current) {
        console.log("→ restarting…");
        recog.start();
      }
    };

    recogRef.current = recog;

    // 1) On demande la permission micro pour faire apparaître la popup
    navigator.mediaDevices
      .getUserMedia({ audio: true })
      .then((stream) => {
        stream.getTracks().forEach((t) => t.stop());
        try {
          recog.start();
        } catch (err) {
          console.error("❌ Cannot start SpeechRecognition:", err);
        }
      })
      .catch((err) => {
        console.error("❌ Microphone permission denied:", err);
      });

    return () => {
      recog.onstart = recog.onresult = recog.onerror = recog.onend = null;
      recog.stop();
    };
  }, [onResult]);
}
