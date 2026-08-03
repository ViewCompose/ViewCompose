import {useLocation} from '@docusaurus/router';
import OriginalLayout from '@theme-original/Layout';
import type {Props} from '@theme/Layout';
import type {ReactNode} from 'react';

export default function Layout(props: Props): ReactNode {
  const {pathname} = useLocation();
  const children = pathname.endsWith('/search') ? <main>{props.children}</main> : props.children;

  return <OriginalLayout {...props}>{children}</OriginalLayout>;
}
