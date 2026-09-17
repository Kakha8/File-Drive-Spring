import test from 'node:test';
import assert from 'node:assert/strict';
import { trashDisplayParent } from '../src/utils/trash.js';

const root = 'users/1/';

test('files and folders deleted from a live folder appear at the trash root', () => {
    assert.equal(trashDisplayParent(`${root}Documents/`, [], root), root);
    assert.equal(trashDisplayParent(`${root}Documents/Nested/`, [], root), root);
    assert.equal(trashDisplayParent(root, [], root), root);
});

test('deleted descendants remain browsable inside their nearest deleted folder', () => {
    const folders = [{ prefix: `${root}Documents/` }, { prefix: `${root}Documents/Nested` }];
    assert.equal(trashDisplayParent(root, folders, root), root);
    assert.equal(trashDisplayParent(`${root}Documents/`, folders, root), `${root}Documents/`);
    assert.equal(trashDisplayParent(`${root}Documents/Nested/`, folders, root), `${root}Documents/Nested/`);
    assert.equal(trashDisplayParent(`${root}Documents/Nested/Live/`, folders, root), `${root}Documents/Nested/`);
});

test('similarly named and unrelated deleted folders do not hide items', () => {
    const folders = [{ prefix: `${root}Doc` }, { prefix: `${root}Other/` }, { prefix: null }];
    assert.equal(trashDisplayParent(`${root}Documents/`, folders, root), root);
});

test('items without an original parent remain visible at the trash root', () => {
    assert.equal(trashDisplayParent('', [], root), root);
    assert.equal(trashDisplayParent('', [], ''), '');
});
