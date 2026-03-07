import { configureStore } from '@reduxjs/toolkit';
import authReducer from './authSlice';
import chatReducer from './chatSlice';
import savedReducer from './savedSlice';
import navigationReducer from './navigationSlice';

export const store = configureStore({
    reducer: {
        auth: authReducer,
        chat: chatReducer,
        saved: savedReducer,
        navigation: navigationReducer,
    },
});

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;

