"use client";

import NextError from "next/error";

/** Paths the locale middleware did not match (e.g. a file-like URL). No locale, so no messages. */
export default function GlobalNotFound() {
  return (
    <html lang="en">
      <body>
        <NextError statusCode={404} />
      </body>
    </html>
  );
}
