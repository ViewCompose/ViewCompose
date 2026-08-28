import useBaseUrl from '@docusaurus/useBaseUrl';
import type {ReactNode} from 'react';

export default function StaticDocumentLink({
  to,
  children,
}: {
  to: string;
  children: ReactNode;
}): ReactNode {
  return <a href={useBaseUrl(to)}>{children}</a>;
}

