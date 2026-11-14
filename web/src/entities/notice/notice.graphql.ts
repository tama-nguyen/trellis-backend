import { graphql } from "@/shared/graphql/generated/gql";

/**
 * GraphQL documents of the notice domain (UI read model, decision T12). `pnpm codegen` turns each
 * into a typed document in `shared/graphql/generated/`; run it after editing a document.
 */
export const NoticeDetailDocument = graphql(`
  query NoticeDetail($code: ID!) {
    notice(code: $code) {
      code
      title
      body
      severity
      audience
      status
      publishedAt
      stats {
        publishCount
        lastPublishedAt
      }
    }
  }
`);
