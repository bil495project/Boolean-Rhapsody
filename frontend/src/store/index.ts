import { configureStore } from '@reduxjs/toolkit';
import authReducer from './authSlice';
import chatReducer from './chatSlice';
import savedReducer from './savedSlice';
import savedRoutesReducer from './savedRoutesSlice';
import navigationReducer from './navigationSlice';
import placesReducer from './placesSlice';
import routeReducer from './routeSlice';

export const store = configureStore({
    reducer: {
        auth: authReducer,
        chat: chatReducer,
        saved: savedReducer,
        savedRoutes: savedRoutesReducer,
        navigation: navigationReducer,
        places: placesReducer,
        route: routeReducer,
    },
});

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;

