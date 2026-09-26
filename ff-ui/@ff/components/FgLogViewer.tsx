import * as React from "preact/compat";
import { useEffect, useRef } from "preact/hooks";
import { JSX } from "preact";

const stripAnsiColors = (input: string): string => {
  const ansiRegex = /\x1B\[[0-9;]*m/g;
  return input.replace(ansiRegex, '');
}

type FgLogViewerProps = {
  logData: string;
};

const FgLogViewer = ({ logData }: FgLogViewerProps): JSX.Element => {
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    const textarea = textareaRef.current;
    if (textarea) {
      textarea.scrollTop = textarea.scrollHeight;
    }
  }, [logData]);

  return (
    <textarea class="editor"
      ref={textareaRef} value={stripAnsiColors(logData)}
      rows={32} readOnly
    />
  );
};

export default FgLogViewer;