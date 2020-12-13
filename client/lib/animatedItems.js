import { useTransition } from 'react-spring';

export default props => {
    const {
        items,
        from,
        update,
        animated,
    } = props;

    const transitions = useTransition(items, item => item.key, {
        from,
        enter: update,
        update,
        leave: from,
        unique: true,
    });

    return transitions.map(({ item, props }) => animated(item, props));
}
